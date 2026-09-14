package io.kestra.plugin.meta.instagram.media;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.facebook.ads.sdk.APIException;
import com.facebook.ads.sdk.IGUser;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.meta.instagram.AbstractInstagramTask;
import io.kestra.plugin.meta.instagram.enums.MediaType;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import io.kestra.core.models.annotations.PluginProperty;

@SuperBuilder
@NoArgsConstructor
@Getter
@Schema(
    title = "Publish an Instagram carousel",
    description = "Creates and publishes a carousel with 2-10 media items (images or videos) to a professional account. Fails if the media count is outside the allowed range."
)
@Plugin(
    examples = {
        @Example(
            title = "Create a carousel post",
            full = true,
            code = """
                id: instagram_create_carousel_post
                namespace: company.team

                tasks:
                  - id: create_carousel_post
                    type: io.kestra.plugin.meta.instagram.media.CreateCarousel
                    igId: "{{ secret('INSTAGRAM_ACCOUNT_ID') }}"
                    accessToken: "{{ secret('INSTAGRAM_ACCESS_TOKEN') }}"
                    mediaUrls:
                      - "https://example.com/image1.jpg"
                      - "https://example.com/image2.jpg"
                      - "https://example.com/video1.mp4"
                    caption: "Check out this amazing carousel!"
                """
        )
    }
)
public class CreateCarousel extends AbstractInstagramTask {

    // Minimum and Maximum number of media items allowed in an Instagram carousel (Meta API requirement).
    private static final int MIN_CAROUSEL_ITEMS = 2;
    private static final int MAX_CAROUSEL_ITEMS = 10;

    @Schema(title = "Media URLs", description = "Public URLs for 2-10 media items; JPEG for images, MP4/MOV for videos.")
    @NotNull
    @PluginProperty(group = "main")
    protected Property<List<String>> mediaUrls;

    @Schema(title = "Caption", description = "Optional caption text for the carousel post.")
    @PluginProperty(group = "advanced")
    protected Property<String> caption;

    @Override
    public Output run(RunContext runContext) throws Exception {
        String rIgId = runContext.render(this.igId).as(String.class).orElseThrow();
        String rToken = runContext.render(this.accessToken).as(String.class).orElseThrow();
        List<String> rMediaUrls = runContext.render(this.mediaUrls).asList(String.class);

        if (rMediaUrls.size() < MIN_CAROUSEL_ITEMS || rMediaUrls.size() > MAX_CAROUSEL_ITEMS) {
            throw new IllegalArgumentException("Carousel must contain between 2 and 10 media items");
        }

        String rCaptionText = runContext.render(this.caption).as(String.class).orElse(null);

        List<String> childContainerIds = new ArrayList<>();
        for (String mediaUrl : rMediaUrls) {
            String containerId = createChildMediaContainer(runContext, rIgId, mediaUrl);
            childContainerIds.add(containerId);
        }

        String carouselContainerId = createCarouselContainer(
            runContext, rIgId, rToken, childContainerIds,
            rCaptionText
        );
        String mediaId = publishMedia(runContext, rIgId, carouselContainerId);

        runContext.logger().info("Successfully created Instagram carousel post with ID: {}", mediaId);

        return Output.builder()
            .mediaId(mediaId)
            .carouselContainerId(carouselContainerId)
            .childContainerIds(childContainerIds)
            .build();
    }

    private String createChildMediaContainer(RunContext runContext, String igId, String mediaUrl)
        throws Exception {
        var request = new IGUser(igId, apiContext(runContext))
            .createMedia()
            .setIsCarouselItem(Boolean.TRUE);

        if (mediaUrl.toLowerCase().endsWith(".mp4") || mediaUrl.toLowerCase().endsWith(".mov")) {
            request.setVideoUrl(mediaUrl).setMediaType("VIDEO");
        } else {
            request.setImageUrl(mediaUrl);
        }

        try {
            return request.execute().getId();
        } catch (APIException e) {
            throw new RuntimeException("Failed to create child media container: %s".formatted(e.getMessage()), e);
        }
    }

    private String createCarouselContainer(RunContext runContext, String igId, String token,
        List<String> childContainerIds, String caption) throws Exception {
        var request = new IGUser(igId, apiContext(runContext))
            .createMedia()
            .setMediaType(MediaType.CAROUSEL.name())
            .setChildren(String.join(",", childContainerIds));

        if (caption != null) {
            request.setCaption(caption);
        }

        try {
            return request.execute().getId();
        } catch (APIException e) {
            throw new RuntimeException("Failed to create carousel container: %s".formatted(e.getMessage()), e);
        }
    }

    private String publishMedia(RunContext runContext, String igId, String containerId)
        throws Exception {
        try {
            return new IGUser(igId, apiContext(runContext))
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
        @Schema(title = "ID of the published carousel media")
        @JsonProperty("mediaId")
        private final String mediaId;

        @Schema(title = "ID of the carousel container")
        @JsonProperty("carouselContainerId")
        private final String carouselContainerId;

        @Schema(title = "IDs of the child media containers")
        @JsonProperty("childContainerIds")
        private final List<String> childContainerIds;

    }
}
