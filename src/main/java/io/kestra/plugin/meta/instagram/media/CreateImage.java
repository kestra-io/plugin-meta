package io.kestra.plugin.meta.instagram.media;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.HttpResponse;
import io.kestra.core.http.client.HttpClient;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.plugin.meta.instagram.AbstractInstagramTask;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

@SuperBuilder
@NoArgsConstructor
@Getter
@EqualsAndHashCode
@Schema(
    title = "Create and publish an image post to Instagram",
    description = "Publish an image with caption to an Instagram professional account"
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

    @Schema(title = "Image URL", description = "Public URL of the image to upload (JPEG format only)")
    @NotNull
    protected Property<String> imageUrl;

    @Schema(title = "Caption", description = "Caption text for the post")
    protected Property<String> caption;

    @Override
    public Output run(RunContext runContext) throws Exception {
        String rIgId = runContext.render(this.igId).as(String.class).orElseThrow();
        String rToken = runContext.render(this.accessToken).as(String.class).orElseThrow();
        String rImageUrl = runContext.render(this.imageUrl).as(String.class).orElseThrow();
        String rCaptionText = runContext.render(this.caption).as(String.class).orElse(null);

        String containerId = createMediaContainer(runContext, rIgId, rToken, rImageUrl, rCaptionText);
        String mediaId = publishMedia(runContext, rIgId, rToken, containerId);

        runContext.logger().info("Successfully created Instagram image post with ID: {}", mediaId);

        return Output.builder()
            .mediaId(mediaId)
            .containerId(containerId)
            .build();
    }

    private String createMediaContainer(RunContext runContext, String igId, String token, String imageUrl,
                                        String caption)
        throws Exception {
        String url = buildApiUrl(runContext, igId + "/media");

        Map<String, Object> containerData = new HashMap<>();
        containerData.put("image_url", imageUrl);

        if (caption != null) {
            containerData.put("caption", caption);
        }

        String jsonBody = JacksonMapper.ofJson().writeValueAsString(containerData);

        HttpRequest request = HttpRequest.builder()
            .method("POST")
            .uri(URI.create(url))
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer " + token)
            .body(HttpRequest.StringRequestBody.builder()
                .content(jsonBody)
                .build())
            .build();


        try (HttpClient httpClient = HttpClient.builder()
            .runContext(runContext)
            .build()) {
            HttpResponse<String> response = httpClient.request(request, String.class);

            if (response.getStatus().getCode() != 200) {
                throw new RuntimeException(
                    "Failed to create media container: " + response.getStatus().getCode()
                        + " - "
                        + response.getBody());
            }

            JsonNode responseJson = JacksonMapper.ofJson().readTree(response.getBody());
            return responseJson.get("id").asText();
        }
    }

    private String publishMedia(RunContext runContext, String igId, String token, String containerId)
        throws Exception {
        String url = buildApiUrl(runContext, igId + "/media_publish");

        Map<String, Object> publishData = new HashMap<>();
        publishData.put("creation_id", containerId);

        String jsonBody = JacksonMapper.ofJson().writeValueAsString(publishData);

        HttpRequest request = HttpRequest.builder()
            .method("POST")
            .uri(URI.create(url))
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer " + token)
            .body(HttpRequest.StringRequestBody.builder()
                .content(jsonBody)
                .build())
            .build();


        try (HttpClient httpClient = HttpClient.builder()
            .runContext(runContext)
            .build()) {
            HttpResponse<String> response = httpClient.request(request, String.class);

            if (response.getStatus().getCode() != 200) {
                throw new RuntimeException(
                    "Failed to publish media: " + response.getStatus().getCode() + " - "
                        + response.getBody());
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
    }
}
