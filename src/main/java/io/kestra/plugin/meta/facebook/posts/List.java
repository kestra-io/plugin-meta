package io.kestra.plugin.meta.facebook.posts;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

import com.facebook.ads.sdk.APIException;
import com.facebook.ads.sdk.Page;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.common.FetchType;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.FileSerde;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.plugin.meta.facebook.AbstractFacebookTask;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@NoArgsConstructor
@Getter
@ToString
@EqualsAndHashCode
@Schema(
    title = "List Facebook Page posts",
    description = "Retrieves Page feed entries via /feed with optional field selection. Limit defaults to 100 (API maximum)."
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
            full = true,
            code = """
                id: list_facebook_posts
                namespace: company.team

                tasks:
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

    @Schema(title = "Fields", description = "Comma-separated Graph fields to include for each post (e.g. id,message,created_time,permalink_url).")
    @PluginProperty(group = "advanced")
    protected Property<String> fields;

    @Schema(title = "Limit", description = "Maximum posts to fetch; capped at 100 by the API.", defaultValue = "100")
    @Builder.Default
    @PluginProperty(group = "processing")
    protected Property<Integer> limit = Property.ofValue(MAX_FETCH_LIMIT);

    @Schema(
        title = "Fetch strategy",
        description = "FETCH (default) returns all rows; FETCH_ONE returns the first; STORE writes all rows to storage as Ion and returns the URI; NONE only counts items."
    )
    @Builder.Default
    @PluginProperty(group = "processing")
    protected Property<FetchType> fetchType = Property.ofValue(FetchType.FETCH);

    @Override
    public Output run(RunContext runContext) throws Exception {
        String rPageId = runContext.render(this.pageId).as(String.class).orElseThrow();
        FetchType rFetchType = runContext.render(this.fetchType).as(FetchType.class).orElse(FetchType.FETCH);

        var request = new Page(rPageId, apiContext(runContext)).getFeed();

        String rFields = runContext.render(this.fields).as(String.class).orElse(null);
        if (rFields != null && !rFields.isEmpty()) {
            request.requestFields(Arrays.asList(rFields.split(",")));
        }

        Integer rLimit = runContext.render(this.limit).as(Integer.class).orElse(MAX_FETCH_LIMIT);
        request.setLimit(rLimit.longValue());

        String rawResponse;
        try {
            // the raw response keeps every field the caller asked for, the typed model would drop the unknown ones
            rawResponse = request.execute().getRawResponse();
        } catch (APIException e) {
            throw new RuntimeException("Failed to list posts: %s".formatted(e.getMessage()), e);
        }

        JsonNode responseJson = JacksonMapper.ofJson().readTree(rawResponse);
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
                try (
                    OutputStream fileOutputStream = new BufferedOutputStream(
                        new FileOutputStream(tempFile),
                        FileSerde.BUFFER_SIZE
                    )
                ) {
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

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "Posts list (when fetchType is FETCH)")
        @JsonProperty("rows")
        private final java.util.List<Map<String, Object>> rows;

        @Schema(title = "Single post (when fetchType is FETCH_ONE)")
        @JsonProperty("row")
        private final Map<String, Object> row;

        @Schema(title = "Stored posts URI (when fetchType is STORE)")
        @JsonProperty("uri")
        private final URI uri;

        @Schema(title = "Total count of posts")
        @JsonProperty("size")
        private final Long size;
    }
}
