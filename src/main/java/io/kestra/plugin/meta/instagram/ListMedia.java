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
import io.kestra.plugin.meta.instagram.enums.MediaField;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@SuperBuilder
@NoArgsConstructor
@Getter
@EqualsAndHashCode
@Schema(
    title = "List Instagram media items",
    description = "Retrieve a list of latest media items from an Instagram professional account"
)
@Plugin(
    examples = {
        @Example(
            title = "List latest media items",
            full = true,
            code = """
                id: instagram_list_media
                namespace: company.team

                tasks:
                  - id: list_media
                    type: io.kestra.plugin.meta.instagram.ListMedia
                    igId: "{{ secret('INSTAGRAM_ACCOUNT_ID') }}"
                    accessToken: "{{ secret('INSTAGRAM_ACCESS_TOKEN') }}"
                    limit: 25
                    fields:
                      - ID
                      - MEDIA_TYPE
                      - MEDIA_URL
                      - PERMALINK
                      - CAPTION
                """
        )
    }
)
public class ListMedia extends AbstractInstagramTask {

    @Schema(title = "Limit", description = "Maximum number of media items to retrieve")
    @Builder.Default
    protected Property<Integer> limit = Property.ofValue(25);

    @Schema(title = "Fields", description = "List of fields to retrieve for each media item")
    @Builder.Default
    protected Property<List<MediaField>> fields = Property.ofValue(List.of(
        MediaField.ID,
        MediaField.MEDIA_TYPE,
        MediaField.MEDIA_URL,
        MediaField.PERMALINK,
        MediaField.THUMBNAIL_URL,
        MediaField.TIMESTAMP,
        MediaField.CAPTION));

    @Override
    public Output run(RunContext runContext) throws Exception {
        String rIgId = runContext.render(this.igId).as(String.class).orElseThrow();
        String rToken = runContext.render(this.accessToken).as(String.class).orElseThrow();
        Integer rLimit = runContext.render(this.limit).as(Integer.class).orElse(25);
        List<MediaField> rFields = runContext.render(this.fields).asList(MediaField.class);

        String fieldsParam = rFields.stream()
            .map(field -> field.name().toLowerCase())
            .collect(Collectors.joining(","));

        String url = buildApiUrl(runContext, rIgId + "/media");

        HttpRequest request = HttpRequest.builder()
            .method("GET")
            .uri(URI.create(url + "?fields=" + fieldsParam + "&limit=" + rLimit))
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer " + rToken)
            .build();

        try (HttpClient httpClient = HttpClient.builder()
            .runContext(runContext)
            .build()) {
            HttpResponse<String> response = httpClient.request(request, String.class);

            if (response.getStatus().getCode() != 200) {
                throw new RuntimeException(
                    "Failed to list media: " + response.getStatus().getCode() + " - "
                        + response.getBody());
            }

            JsonNode responseJson = JacksonMapper.ofJson().readTree(response.getBody());
            JsonNode dataNode = responseJson.get("data");

            List<MediaItem> mediaItems = new ArrayList<>();
            if (dataNode != null && dataNode.isArray()) {
                for (JsonNode mediaNode : dataNode) {
                    MediaItem item = MediaItem.builder()
                        .id(mediaNode.has("id") ? mediaNode.get("id").asText() : null)
                        .mediaType(mediaNode.has("media_type")
                            ? mediaNode.get("media_type").asText()
                            : null)
                        .mediaUrl(mediaNode.has("media_url")
                            ? mediaNode.get("media_url").asText()
                            : null)
                        .permalink(mediaNode.has("permalink")
                            ? mediaNode.get("permalink").asText()
                            : null)
                        .thumbnailUrl(
                            mediaNode.has("thumbnail_url")
                                ? mediaNode.get("thumbnail_url")
                                .asText()
                                : null)
                        .timestamp(mediaNode.has("timestamp")
                            ? mediaNode.get("timestamp").asText()
                            : null)
                        .caption(mediaNode.has("caption")
                            ? mediaNode.get("caption").asText()
                            : null)
                        .build();
                    mediaItems.add(item);
                }
            }

            runContext.logger().info("Successfully retrieved {} media items", mediaItems.size());

            return Output.builder()
                .mediaItems(mediaItems)
                .totalCount(mediaItems.size())
                .build();
        }
    }

    @Builder
    @Getter
    public static class MediaItem {
        @Schema(title = "Media ID")
        @JsonProperty("id")
        private final String id;

        @Schema(title = "Media type")
        @JsonProperty("mediaType")
        private final String mediaType;

        @Schema(title = "Media URL")
        @JsonProperty("mediaUrl")
        private final String mediaUrl;

        @Schema(title = "Permalink")
        @JsonProperty("permalink")
        private final String permalink;

        @Schema(title = "Thumbnail URL")
        @JsonProperty("thumbnailUrl")
        private final String thumbnailUrl;

        @Schema(title = "Timestamp")
        @JsonProperty("timestamp")
        private final String timestamp;

        @Schema(title = "Caption")
        @JsonProperty("caption")
        private final String caption;
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "List of media items")
        @JsonProperty("mediaItems")
        private final List<MediaItem> mediaItems;

        @Schema(title = "Total count of media items")
        @JsonProperty("totalCount")
        private final Integer totalCount;
    }
}
