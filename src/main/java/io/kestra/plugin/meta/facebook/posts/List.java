package io.kestra.plugin.meta.facebook.posts;

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
import io.kestra.plugin.meta.facebook.AbstractFacebookTask;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Map;

@SuperBuilder
@NoArgsConstructor
@Getter
@ToString
@EqualsAndHashCode
@Schema(
    title = "List posts from a Facebook Page",
    description = "Retrieve a list of recent posts from a Facebook Page"
)
@Plugin(
    examples = {
        @Example(
            title = "List Facebook page posts",
            full = true,
            code = """
                id: facebook_list_posts
                namespace: company.team

                tasks:
                  - id: list_posts
                    type: io.kestra.plugin.meta.facebook.posts.List
                    pageId: "{{ secret('FACEBOOK_PAGE_ID') }}"
                    accessToken: "{{ secret('FACEBOOK_ACCESS_TOKEN') }}"
                    limit: 10
                """
        ),
        @Example(
            title = "List posts with specific fields",
            code = """
                - id: list_detailed_posts
                  type: io.kestra.plugin.meta.facebook.posts.List
                  pageId: "{{ secret('FACEBOOK_PAGE_ID') }}"
                  accessToken: "{{ secret('FACEBOOK_ACCESS_TOKEN') }}"
                  limit: 5
                  fields: "id,message,created_time,permalink_url,reactions.summary(true)"
                """
        )
    }
)
public class List extends AbstractFacebookTask {

    // The maximum number of posts that can be fetched with the Meta API
    private static final int MAX_FETCH_LIMIT = 100;

    @Schema(title = "Fields", description = "Comma-separated list of fields to retrieve for each post (e.g., id,message,created_time,permalink_url)")
    protected Property<String> fields;

    @Schema(title = "Limit", description = "Maximum number of posts to retrieve", defaultValue = "100")
    @Builder.Default
    protected Property<Integer> limit = Property.ofValue(MAX_FETCH_LIMIT);

    @Schema(title = "The way you want to store the data.", description = "FETCH_ONE output the first row, "
        + "FETCH output all rows, "
        + "STORE store all rows in a file, "
        + "NONE do nothing.")
    @Builder.Default
    protected Property<FetchType> fetchType = Property.ofValue(FetchType.FETCH);

    @Override
    public Output run(RunContext runContext) throws Exception {
        String rPageId = runContext.render(this.pageId).as(String.class).orElseThrow();
        String rToken = runContext.render(this.accessToken).as(String.class).orElseThrow();
        FetchType rFetchType = runContext.render(this.fetchType).as(FetchType.class).orElse(FetchType.FETCH);

        StringBuilder urlBuilder = new StringBuilder();
        urlBuilder.append(buildApiUrl(runContext, rPageId + "/feed"));

        boolean hasParams = false;

        String rFields = runContext.render(this.fields).as(String.class).orElse(null);
        if (rFields != null && !rFields.isEmpty()) {
            urlBuilder.append("?fields=").append(rFields);
            hasParams = true;
        }

        Integer rLimit = runContext.render(this.limit).as(Integer.class).orElse(MAX_FETCH_LIMIT);
        urlBuilder.append(hasParams ? "&" : "?").append("limit=").append(rLimit);

        String fullUrl = urlBuilder.toString();

        HttpRequest request = HttpRequest.builder()
            .uri(URI.create(fullUrl))
            .method("GET")
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer " + rToken)
            .build();

        try (HttpClient httpClient = HttpClient.builder()
            .runContext(runContext)
            .build()) {
            HttpResponse<String> response = httpClient.request(request, String.class);

            if (response.getStatus().getCode() != 200) {
                throw new RuntimeException(
                    "Failed to list posts: " + response.getStatus().getCode() + " - " + response.getBody());
            }

            JsonNode responseJson = JacksonMapper.ofJson().readTree(response.getBody());
            JsonNode dataArray = responseJson.get("data");

            Output.OutputBuilder output = Output.builder();
            long size = 0L;

            switch (rFetchType) {
                case FETCH_ONE -> {
                    Map<String, Object> result = null;
                    if (dataArray != null && dataArray.isArray() && !dataArray.isEmpty()) {
                        result = JacksonMapper.ofJson().convertValue(dataArray.get(0), Map.class);
                    }
                    size = result == null ? 0L : 1L;
                    output.row(result);
                }
                case STORE -> {
                    File tempFile = runContext.workingDir().createTempFile(".ion").toFile();
                    try (OutputStream fileOutputStream = new BufferedOutputStream(new FileOutputStream(tempFile),
                        FileSerde.BUFFER_SIZE)) {
                        if (dataArray != null && dataArray.isArray()) {
                            for (JsonNode postNode : dataArray) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> post = JacksonMapper.ofJson().convertValue(postNode, Map.class);
                                FileSerde.write(fileOutputStream, post);
                                size++;
                            }
                        }
                    }
                    output.uri(runContext.storage().putFile(tempFile));
                }
                case FETCH -> {
                    java.util.List<Map<String, Object>> posts = new ArrayList<>();
                    if (dataArray != null && dataArray.isArray()) {
                        for (JsonNode postNode : dataArray) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> post = JacksonMapper.ofJson().convertValue(postNode, Map.class);
                            posts.add(post);
                            size++;
                        }
                    }
                    output.rows(posts);
                }
                case NONE -> {
                    if (dataArray != null && dataArray.isArray()) {
                        size = dataArray.size();
                    }
                }
            }

            output.size(size);
            runContext.logger().info("Successfully retrieved {} Facebook posts", size);

            return output.build();
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "List of posts (when fetchType is FETCH)")
        @JsonProperty("rows")
        private final java.util.List<Map<String, Object>> rows;

        @Schema(title = "Single post (when fetchType is FETCH_ONE)")
        @JsonProperty("row")
        private final Map<String, Object> row;

        @Schema(title = "URI of stored posts file (when fetchType is STORE)")
        @JsonProperty("uri")
        private final URI uri;

        @Schema(title = "Total count of posts")
        @JsonProperty("size")
        private final Long size;
    }
}