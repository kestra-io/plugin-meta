package io.kestra.plugin.meta.instagram.media;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.HttpResponse;
import io.kestra.core.http.client.HttpClient;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.common.FetchType;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.FileSerde;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.plugin.meta.instagram.AbstractInstagramTask;
import io.kestra.plugin.meta.instagram.enums.MediaField;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@SuperBuilder
@NoArgsConstructor
@Getter
@EqualsAndHashCode
@Schema(
    title = "List Instagram media items",
    description = "Retrieve a list of the latest media items from an Instagram professional account"
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
                    type: io.kestra.plugin.meta.instagram.media.List
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
public class List extends AbstractInstagramTask {

    // Default "limit" value is 25, as per the default in the Meta Graph API
    private static final int DEFAULT_MEDIA_LIMIT = 25;

    @Schema(title = "Limit", description = "Maximum number of media items to retrieve")
    @Builder.Default
    protected Property<Integer> limit = Property.ofValue(DEFAULT_MEDIA_LIMIT);

    @Schema(title = "Fields", description = "List of fields to retrieve for each media item")
    @Builder.Default
    protected Property<java.util.List<MediaField>> fields = Property.ofValue(java.util.List.of(
        MediaField.ID,
        MediaField.MEDIA_TYPE,
        MediaField.MEDIA_URL,
        MediaField.PERMALINK,
        MediaField.THUMBNAIL_URL,
        MediaField.TIMESTAMP,
        MediaField.CAPTION));

    @Schema(title = "The way you want to store the data.", description = "FETCH_ONE output the first row, "
        + "FETCH output all rows, "
        + "STORE store all rows in a file, "
        + "NONE do nothing.")
    @Builder.Default
    protected Property<FetchType> fetchType = Property.ofValue(FetchType.FETCH);

    @Override
    public Output run(RunContext runContext) throws Exception {
        String rIgId = runContext.render(this.igId).as(String.class).orElseThrow();
        String rToken = runContext.render(this.accessToken).as(String.class).orElseThrow();
        Integer rLimit = runContext.render(this.limit).as(Integer.class).orElse(DEFAULT_MEDIA_LIMIT);
        java.util.List<MediaField> rFields = runContext.render(this.fields).asList(MediaField.class);
        FetchType rFetchType = runContext.render(this.fetchType).as(FetchType.class).orElse(FetchType.FETCH);

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

            Output.OutputBuilder output = Output.builder();
            long size = 0L;

            switch (rFetchType) {
                case FETCH_ONE -> {
                    Map<String, Object> result = null;
                    if (dataNode != null && dataNode.isArray() && !dataNode.isEmpty()) {
                        result = convertNodeToMap(dataNode.get(0));
                    }
                    size = result == null ? 0L : 1L;
                    output.row(result);
                }
                case STORE -> {
                    File tempFile = runContext.workingDir().createTempFile(".ion").toFile();
                    try (OutputStream fileOutputStream = new BufferedOutputStream(new FileOutputStream(tempFile),
                        FileSerde.BUFFER_SIZE)) {
                        if (dataNode != null && dataNode.isArray()) {
                            for (JsonNode mediaNode : dataNode) {
                                Map<String, Object> map = convertNodeToMap(mediaNode);
                                FileSerde.write(fileOutputStream, map);
                                size++;
                            }
                        }
                    }
                    output.uri(runContext.storage().putFile(tempFile));
                }
                case FETCH -> {
                    java.util.List<Map<String, Object>> maps = new ArrayList<>();
                    if (dataNode != null && dataNode.isArray()) {
                        for (JsonNode mediaNode : dataNode) {
                            maps.add(convertNodeToMap(mediaNode));
                            size++;
                        }
                    }
                    output.rows(maps);
                }
                case NONE -> {
                    if (dataNode != null && dataNode.isArray()) {
                        size = dataNode.size();
                    }
                }
            }

            output.size(size);
            runContext.logger().info("Successfully retrieved {} media items", size);

            return output.build();
        }
    }

    private Map<String, Object> convertNodeToMap(JsonNode mediaNode) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", mediaNode.has("id") ? mediaNode.get("id").asText() : null);
        map.put("mediaType", mediaNode.has("media_type") ? mediaNode.get("media_type").asText() : null);
        map.put("mediaUrl", mediaNode.has("media_url") ? mediaNode.get("media_url").asText() : null);
        map.put("permalink", mediaNode.has("permalink") ? mediaNode.get("permalink").asText() : null);
        map.put("thumbnailUrl", mediaNode.has("thumbnail_url") ? mediaNode.get("thumbnail_url").asText() : null);
        map.put("timestamp", mediaNode.has("timestamp") ? mediaNode.get("timestamp").asText() : null);
        map.put("caption", mediaNode.has("caption") ? mediaNode.get("caption").asText() : null);
        return map;
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "List of media items (when fetchType is FETCH)")
        @JsonProperty("rows")
        private final java.util.List<Map<String, Object>> rows;

        @Schema(title = "Single media item (when fetchType is FETCH_ONE)")
        @JsonProperty("row")
        private final Map<String, Object> row;

        @Schema(title = "URI of stored media items file (when fetchType is STORE)")
        @JsonProperty("uri")
        private final URI uri;

        @Schema(title = "Total count of media items")
        @JsonProperty("size")
        private final Long size;
    }
}
