package io.kestra.plugin.meta.instagram.media;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.facebook.ads.sdk.APIContext;
import com.facebook.ads.sdk.APIException;
import com.facebook.ads.sdk.IGMedia;
import com.facebook.ads.sdk.IGUser;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.utils.Await;
import io.kestra.plugin.meta.instagram.AbstractInstagramTask;
import io.kestra.plugin.meta.instagram.enums.VideoType;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@NoArgsConstructor
@Getter
@Schema(
    title = "Publish an Instagram video post",
    description = "Uploads a video URL and publishes it as a feed post or Reel. Waits up to 5 minutes for processing before publishing."
)
@Plugin(
    examples = {
        @Example(
            title = "Create a video post",
            full = true,
            code = """
                id: instagram_create_video_post
                namespace: company.team

                tasks:
                  - id: create_video_post
                    type: io.kestra.plugin.meta.instagram.media.CreateVideo
                    igId: "{{ secret('INSTAGRAM_ACCOUNT_ID') }}"
                    accessToken: "{{ secret('INSTAGRAM_ACCESS_TOKEN') }}"
                    videoUrl: "https://example.com/video.mp4"
                    caption: "Check out this amazing video!"
                    VideoType: REELS
                """
        )
    }
)
public class CreateVideo extends AbstractInstagramTask {
    /** Matches the read timeout the pre-SDK poll set on its HTTP client. */
    private static final Duration POLL_TIMEOUT = Duration.ofSeconds(30);

    /** Its own pool, so a hung poll cannot occupy a shared ForkJoinPool.commonPool thread. */
    private static final java.util.concurrent.ExecutorService POLL_EXECUTOR = java.util.concurrent.Executors.newCachedThreadPool(
        r ->
        {
            var thread = new Thread(r, "instagram-media-poll");
            thread.setDaemon(true);

            return thread;
        }
    );

    @Schema(title = "Video URL", description = "Public HTTPS URL of the video to upload (e.g. MP4).")
    @NotNull
    @PluginProperty(group = "main")
    protected Property<String> videoUrl;

    @Schema(title = "Caption", description = "Optional caption text for the post.")
    @PluginProperty(group = "advanced")
    protected Property<String> caption;

    @Schema(title = "Media type", description = "Video media type to create (VIDEO or REELS). Defaults to VIDEO.")
    @Builder.Default
    @PluginProperty(group = "advanced")
    protected Property<VideoType> videoType = Property.ofValue(VideoType.VIDEO);

    @Override
    public Output run(RunContext runContext) throws Exception {
        String rIgId = runContext.render(this.igId).as(String.class).orElseThrow();
        String rVideoUrl = runContext.render(this.videoUrl).as(String.class).orElseThrow();
        VideoType rVideoType = runContext.render(this.videoType).as(VideoType.class).orElse(VideoType.VIDEO);
        String rCaptionText = runContext.render(this.caption).as(String.class).orElse(null);

        runContext.logger().info("Creating Instagram {} post with video from: {}", rVideoType, rVideoUrl);

        var context = apiContext(runContext);

        String containerId = createMediaContainer(context, rIgId, rVideoUrl, rVideoType, rCaptionText);
        runContext.logger().info("Media container created with ID: {}", containerId);

        // Wait for video processing to complete
        waitForContainerReady(runContext, context, containerId);
        runContext.logger().info("Video processing completed for container: {}", containerId);

        String mediaId = publishMedia(context, rIgId, containerId);

        runContext.logger().info("Successfully created Instagram video post with ID: {}", mediaId);

        return Output.builder()
            .mediaId(mediaId)
            .containerId(containerId)
            .build();
    }

    private String createMediaContainer(APIContext context, String igId, String videoUrl,
        VideoType VideoType, String caption) {
        var request = new IGUser(igId, context)
            .createMedia()
            .setVideoUrl(videoUrl)
            .setMediaType(VideoType.name());

        if (caption != null) {
            request.setCaption(caption);
        }

        try {
            return request.execute().getId();
        } catch (APIException e) {
            throw new RuntimeException("Failed to create container : %s".formatted(e.getMessage()), e);
        }
    }

    private void waitForContainerReady(RunContext runContext, APIContext context, String containerId) throws Exception {
        runContext.logger().info("Waiting for video processing to complete for container: {}", containerId);

        try {
            Await.until(
                () ->
                {
                    try {
                        return checkContainerStatus(runContext, context, containerId);
                    } catch (Exception e) {
                        return false;
                    }
                },
                Duration.ofSeconds(10),
                Duration.ofMinutes(5)
            );
        } catch (TimeoutException e) {
            throw new RuntimeException("Timed out after 5 minutes while waiting for video processing to complete for container: " + containerId, e);
        }
    }

    private boolean checkContainerStatus(RunContext runContext, APIContext context, String containerId) throws Exception {
        // the SDK exposes no timeout seam and HttpsURLConnection defaults to infinite, and Await only checks its
        // deadline between polls, so a hung call has to be bounded here or the 5 minute ceiling never fires
        String rawResponse;
        try {
            var future = CompletableFuture
                .supplyAsync(
                    () ->
                    {
                        try {
                            return new IGMedia(containerId, context)
                                .get()
                                .requestField("status_code")
                                .execute()
                                .getRawResponse();
                        } catch (APIException e) {
                            throw new CompletionException(e);
                        }
                    },
                    POLL_EXECUTOR
                );

            try {
                rawResponse = future.get(POLL_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                // get() only stops waiting, the call itself keeps running until the socket gives up
                future.cancel(true);
                runContext.logger().debug("Container {} status poll timed out after {}", containerId, POLL_TIMEOUT);

                return false;
            }
        } catch (ExecutionException e) {
            throw new RuntimeException("Failed to read the status of container %s".formatted(containerId), e.getCause());
        }

        JsonNode responseJson = JacksonMapper.ofJson().readTree(rawResponse);
        String statusCode = responseJson.has("status_code")
            ? responseJson.get("status_code").asText()
            : null;

        runContext.logger().debug("Container {} status: {}", containerId, statusCode);

        if ("FINISHED".equals(statusCode)) {
            runContext.logger().info("Video processing completed for container: {}", containerId);
            return true; // Processing complete
        } else if ("ERROR".equals(statusCode)) {
            throw new RuntimeException("Video processing failed for container: " + containerId);
        }
        // Status is IN_PROGRESS, continue waiting
        runContext.logger().debug("Video still processing, status: {}", statusCode);

        return false; // Not ready yet
    }

    private String publishMedia(APIContext context, String igId, String containerId) {
        try {
            return new IGUser(igId, context)
                .createMediaPublish()
                .setCreationId(containerId)
                .execute()
                .getId();
        } catch (APIException e) {
            throw new RuntimeException("Failed to publish media: %s".formatted(e.getMessage()), e);
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "ID of the published media")
        @JsonProperty("mediaId")
        private final String mediaId;

        @Schema(title = "ID of the media container")
        @JsonProperty("containerId")
        private final String containerId;
    }
}
