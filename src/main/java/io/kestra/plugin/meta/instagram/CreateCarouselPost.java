package io.kestra.plugin.meta.instagram;

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
import io.kestra.plugin.meta.instagram.enums.MediaType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuperBuilder
@NoArgsConstructor
@Getter
@Schema(
    title = "Create and publish a carousel post to Instagram",
    description = "Publish a multi-image or multi-video carousel to an Instagram professional account"
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
                    type: io.kestra.plugin.meta.instagram.CreateCarouselPost
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
public class CreateCarouselPost extends AbstractInstagramTask {

    @Schema(title = "Media URLs", description = "List of public URLs for images and videos (2-10 items, JPEG for images)")
    @NotNull
    protected Property<List<String>> mediaUrls;

    @Schema(title = "Caption", description = "Caption text for the carousel post")
    protected Property<String> caption;

    @Override
    public Output run(RunContext runContext) throws Exception {
        String rIgId = runContext.render(this.igId).as(String.class).orElseThrow();
        String rToken = runContext.render(this.accessToken).as(String.class).orElseThrow();
        List<String> rMediaUrls = runContext.render(this.mediaUrls).asList(String.class);

        if (rMediaUrls.size() < 2 || rMediaUrls.size() > 10) {
            throw new IllegalArgumentException("Carousel must contain between 2 and 10 media items");
        }

        String captionText = runContext.render(this.caption).as(String.class).orElse(null);

        List<String> childContainerIds = new ArrayList<>();
        for (String mediaUrl : rMediaUrls) {
            String containerId = createChildMediaContainer(runContext, rIgId, rToken, mediaUrl);
            childContainerIds.add(containerId);
        }

        String carouselContainerId = createCarouselContainer(runContext, rIgId, rToken, childContainerIds,
            captionText);
        String mediaId = publishMedia(runContext, rIgId, rToken, carouselContainerId);

        runContext.logger().info("Successfully created Instagram carousel post with ID: {}", mediaId);

        return Output.builder()
            .mediaId(mediaId)
            .carouselContainerId(carouselContainerId)
            .childContainerIds(childContainerIds)
            .mediaUrls(rMediaUrls)
            .caption(captionText)
            .build();
    }

    private String createChildMediaContainer(RunContext runContext, String igId, String token, String mediaUrl)
        throws Exception {
        String url = buildApiUrl(runContext, igId + "/media");

        Map<String, Object> containerData = new HashMap<>();
        containerData.put("is_carousel_item", true);

        if (mediaUrl.toLowerCase().endsWith(".mp4") || mediaUrl.toLowerCase().endsWith(".mov")) {
            containerData.put("video_url", mediaUrl);
            containerData.put("media_type", "VIDEO");
        } else {
            containerData.put("image_url", mediaUrl);
        }

        String jsonBody = JacksonMapper.ofJson().writeValueAsString(containerData);

        HttpRequest request = HttpRequest.builder()
            .method("POST")
            .uri(URI.create(url))
            .body(HttpRequest.StringRequestBody.builder()
                .content(jsonBody)
                .build())
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer " + token)
            .build();


        try (HttpClient httpClient = HttpClient.builder()
            .runContext(runContext)
            .build()) {
            HttpResponse<String> response = httpClient.request(request, String.class);

            if (response.getStatus().getCode() != 200) {
                throw new RuntimeException(
                    "Failed to create child media container: "
                        + response.getStatus().getCode() + " - "
                        + response.getBody());
            }

            JsonNode responseJson = JacksonMapper.ofJson().readTree(response.getBody());
            return responseJson.get("id").asText();
        }
    }

    private String createCarouselContainer(RunContext runContext, String igId, String token,
                                           List<String> childContainerIds, String caption) throws Exception {
        String url = buildApiUrl(runContext, igId + "/media");

        Map<String, Object> containerData = new HashMap<>();
        containerData.put("media_type", MediaType.CAROUSEL.name());
        containerData.put("children", String.join(",", childContainerIds));

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
                    "Failed to create carousel container: " + response.getStatus().getCode()
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
        @Schema(title = "The ID of the published carousel media")
        @JsonProperty("mediaId")
        private final String mediaId;

        @Schema(title = "The ID of the carousel container")
        @JsonProperty("carouselContainerId")
        private final String carouselContainerId;

        @Schema(title = "The IDs of the child media containers")
        @JsonProperty("childContainerIds")
        private final List<String> childContainerIds;

        @Schema(title = "The URLs of the media items")
        @JsonProperty("mediaUrls")
        private final List<String> mediaUrls;

        @Schema(title = "The caption of the carousel post")
        @JsonProperty("caption")
        private final String caption;
    }
}
