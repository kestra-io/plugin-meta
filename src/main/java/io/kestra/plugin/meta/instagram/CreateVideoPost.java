package io.kestra.plugin.meta.instagram;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.HttpResponse;
import io.kestra.core.http.client.HttpClient;
import io.kestra.core.http.client.configurations.HttpConfiguration;
import io.kestra.core.http.client.configurations.TimeoutConfiguration;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.plugin.meta.instagram.enums.MediaType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.awaitility.core.ConditionTimeoutException;

import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

import static org.awaitility.Awaitility.await;

@SuperBuilder
@NoArgsConstructor
@Getter
@Schema(
    title = "Create and publish a video post to Instagram",
    description = "Publish a video with caption to an Instagram professional account"
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
                    type: io.kestra.plugin.meta.instagram.CreateVideoPost
                    igId: "{{ secret('INSTAGRAM_ACCOUNT_ID') }}"
                    accessToken: "{{ secret('INSTAGRAM_ACCESS_TOKEN') }}"
                    videoUrl: "https://example.com/video.mp4"
                    caption: "Check out this amazing video!"
                    mediaType: REELS
                """
        )
    }
)
public class CreateVideoPost extends AbstractInstagramTask {

    @Schema(title = "Video URL", description = "Public URL of the video to upload")
    @NotNull
    protected Property<String> videoUrl;

    @Schema(title = "Caption", description = "Caption text for the post")
    protected Property<String> caption;

    @Schema(title = "Media Type", description = "Type of video media to create")
    @Builder.Default
    protected Property<MediaType> mediaType = Property.ofValue(MediaType.VIDEO);

    @Override
    public Output run(RunContext runContext) throws Exception {
        String rIgId = runContext.render(this.igId).as(String.class).orElseThrow();
        String rToken = runContext.render(this.accessToken).as(String.class).orElseThrow();
        String rVideoUrl = runContext.render(this.videoUrl).as(String.class).orElseThrow();
        MediaType rMediaType = runContext.render(this.mediaType).as(MediaType.class).orElse(MediaType.VIDEO);

        runContext.logger().info("Creating Instagram {} post with video from: {}", rMediaType, rVideoUrl);

        String captionText = null;
        if (this.caption != null) {
            captionText = runContext.render(this.caption).as(String.class).orElse(null);
        }

        String containerId = createMediaContainer(runContext, rIgId, rToken, rVideoUrl, rMediaType, captionText);
        runContext.logger().info("Media container created with ID: {}", containerId);

        // Wait for video processing to complete
        waitForContainerReady(runContext, rToken, containerId);
        runContext.logger().info("Video processing completed for container: {}", containerId);

        String mediaId = publishMedia(runContext, rIgId, rToken, containerId);

        runContext.logger().info("Successfully created Instagram video post with ID: {}", mediaId);

        return Output.builder()
            .mediaId(mediaId)
            .containerId(containerId)
            .videoUrl(rVideoUrl)
            .caption(captionText)
            .mediaType(rMediaType.name())
            .build();
    }

    private String createMediaContainer(RunContext runContext, String igId, String token, String videoUrl,
                                        MediaType mediaType, String caption) throws Exception {
        String url = buildApiUrl(runContext, igId + "/media");

        Map<String, Object> containerData = new HashMap<>();
        containerData.put("video_url", videoUrl);
        containerData.put("media_type", mediaType.name());

        if (caption != null) {
            containerData.put("caption", caption);
        }

        String jsonBody = JacksonMapper.ofJson().writeValueAsString(containerData);

        HttpRequest request = HttpRequest.builder()
            .method("POST")
            .uri(URI.create(url))
            .body(HttpRequest.StringRequestBody.builder()
                .content(jsonBody)
                .contentType("application/json")
                .build())
            .addHeader("Authorization", "Bearer " + token)
            .addHeader("Content-Type", "application/json")
            .build();

        HttpConfiguration httpConfiguration = HttpConfiguration.builder().build();

        try (HttpClient httpClient = HttpClient.builder()
            .configuration(httpConfiguration)
            .runContext(runContext)
            .build()) {
            HttpResponse<String> response = httpClient.request(request, String.class);

            if (response.getStatus().getCode() != 200) {
                throw new RuntimeException("Failed to create container : " + response.getStatus().getCode());
            }

            JsonNode responseJson = JacksonMapper.ofJson().readTree(response.getBody());
            return responseJson.get("id").asText();
        }
    }

    private void waitForContainerReady(RunContext runContext, String token, String containerId)
        throws Exception {
        String url = buildApiUrl(runContext, containerId);

        runContext.logger().info("Waiting for video processing to complete for container: {}", containerId);

        try {
            await()
                .atMost(Duration.ofMinutes(5))
                .pollInterval(Duration.ofSeconds(10))
                .pollDelay(Duration.ofSeconds(2))
                .ignoreExceptions()
                .until(checkContainerStatus(runContext, url, token, containerId));
        } catch (ConditionTimeoutException e) {
            throw new RuntimeException(
                "Video processing timeout after 5 minutes for container: " + containerId, e);
        }
    }

    private Callable<Boolean> checkContainerStatus(RunContext runContext, String url, String token,
                                                   String containerId) {
        return () -> {
            HttpRequest request = HttpRequest.builder()
                .method("GET")
                .uri(URI.create(url + "?fields=status_code"))
                .addHeader("Authorization", "Bearer " + token)
                .build();

            HttpConfiguration httpConfiguration = HttpConfiguration.builder()
                .timeout(
                    TimeoutConfiguration.builder()
                        .readIdleTimeout(Property.ofValue(Duration.ofSeconds(30)))
                        .build())
                .build();

            try (HttpClient httpClient = HttpClient.builder()
                .configuration(httpConfiguration)
                .runContext(runContext)
                .build()) {
                HttpResponse<String> response = httpClient.request(request, String.class);

                if (response.getStatus().getCode() == 200) {
                    JsonNode responseJson = JacksonMapper.ofJson().readTree(response.getBody());
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
                }
                return false; // Not ready yet
            }
        };
    }

    private String publishMedia(RunContext runContext, String igId, String token, String containerId) throws Exception {

        String url = buildApiUrl(runContext, igId + "/media_publish");

        Map<String, Object> publishData = new HashMap<>();
        publishData.put("creation_id", containerId);

        String jsonBody = JacksonMapper.ofJson().writeValueAsString(publishData);

        HttpRequest request = HttpRequest.builder()
            .method("POST")
            .uri(URI.create(url))
            .body(HttpRequest.StringRequestBody.builder()
                .content(jsonBody)
                .contentType("application/json")
                .build())
            .addHeader("Authorization", "Bearer " + token)
            .addHeader("Content-Type", "application/json")
            .build();

        HttpConfiguration httpConfiguration = HttpConfiguration.builder().build();

        try (HttpClient httpClient = HttpClient.builder()
            .configuration(httpConfiguration)
            .runContext(runContext)
            .build()) {
            HttpResponse<String> response = httpClient.request(request, String.class);

            if (response.getStatus().getCode() != 200) {
                throw new RuntimeException(
                    "Failed to publish media: " + response.getStatus().getCode() + " - " + response.getBody());
            }

            JsonNode responseJson = JacksonMapper.ofJson().readTree(response.getBody());
            return responseJson.get("id").asText();
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "The ID of the published media")
        @JsonProperty("mediaId")
        private final String mediaId;

        @Schema(title = "The ID of the media container")
        @JsonProperty("containerId")
        private final String containerId;

        @Schema(title = "The URL of the uploaded video")
        @JsonProperty("videoUrl")
        private final String videoUrl;

        @Schema(title = "The caption of the post")
        @JsonProperty("caption")
        private final String caption;

        @Schema(title = "The media type of the post")
        @JsonProperty("mediaType")
        private final String mediaType;
    }
}
