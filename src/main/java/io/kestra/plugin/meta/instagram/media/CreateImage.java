package io.kestra.plugin.meta.instagram.media;


import com.fasterxml.jackson.annotation.JsonProperty;

import com.facebook.ads.sdk.APIException;
import com.facebook.ads.sdk.IGUser;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.meta.instagram.AbstractInstagramTask;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import io.kestra.core.models.annotations.PluginProperty;

@SuperBuilder
@NoArgsConstructor
@Getter
@EqualsAndHashCode
@Schema(
    title = "Publish an Instagram image post",
    description = "Uploads an image URL to the Instagram Graph API and publishes it with an optional caption. Requires a professional account token with content publish rights."
)
@Plugin(
    examples = {
        @Example(
            title = "Create an image post",
            full = true,
            code = """
                id: instagram_create_image_post
                namespace: company.team

                tasks:
                  - id: create_image_post
                    type: io.kestra.plugin.meta.instagram.media.CreateImage
                    igId: "{{ secret('INSTAGRAM_ACCOUNT_ID') }}"
                    accessToken: "{{ secret('INSTAGRAM_ACCESS_TOKEN') }}"
                    imageUrl: "https://example.com/image.jpg"
                    caption: "Hello from Kestra! This is an automated post."
                """
        )
    }
)
public class CreateImage extends AbstractInstagramTask {

    @Schema(title = "Image URL", description = "Public HTTPS URL of the image to upload (JPEG only).")
    @NotNull
    @PluginProperty(group = "main")
    protected Property<String> imageUrl;

    @Schema(title = "Caption", description = "Optional caption text for the post.")
    @PluginProperty(group = "advanced")
    protected Property<String> caption;

    @Override
    public Output run(RunContext runContext) throws Exception {
        String rIgId = runContext.render(this.igId).as(String.class).orElseThrow();
        String rToken = runContext.render(this.accessToken).as(String.class).orElseThrow();
        String rImageUrl = runContext.render(this.imageUrl).as(String.class).orElseThrow();
        String rCaptionText = runContext.render(this.caption).as(String.class).orElse(null);

        String containerId = createMediaContainer(runContext, rIgId, rImageUrl, rCaptionText);
        String mediaId = publishMedia(runContext, rIgId, containerId);

        runContext.logger().info("Successfully created Instagram image post with ID: {}", mediaId);

        return Output.builder()
            .mediaId(mediaId)
            .containerId(containerId)
            .build();
    }

    private String createMediaContainer(RunContext runContext, String igId, String imageUrl,
        String caption)
        throws Exception {
        var request = new IGUser(igId, apiContext(runContext))
            .createMedia()
            .setImageUrl(imageUrl);

        if (caption != null) {
            request.setCaption(caption);
        }

        try {
            return request.execute().getId();
        } catch (APIException e) {
            throw new RuntimeException("Failed to create media container: %s".formatted(e.getMessage()), e);
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
        @Schema(title = "ID of the published media")
        @JsonProperty("mediaId")
        private final String mediaId;

        @Schema(title = "ID of the media container")
        @JsonProperty("containerId")
        private final String containerId;
    }
}
