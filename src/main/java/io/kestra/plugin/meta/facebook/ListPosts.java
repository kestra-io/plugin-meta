package io.kestra.plugin.meta.facebook;

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
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
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
                    type: io.kestra.plugin.meta.facebook.ListPosts
                    pageId: "{{ secret('FACEBOOK_PAGE_ID') }}"
                    accessToken: "{{ secret('FACEBOOK_ACCESS_TOKEN') }}"
                    limit: 10
                """
        ),
        @Example(
            title = "List posts with specific fields",
            code = """
                - id: list_detailed_posts
                  type: io.kestra.plugin.meta.facebook.ListPosts
                  pageId: "{{ secret('FACEBOOK_PAGE_ID') }}"
                  accessToken: "{{ secret('FACEBOOK_ACCESS_TOKEN') }}"
                  limit: 5
                  fields: "id,message,created_time,permalink_url,reactions.summary(true)"
                """
        )
    }
)
public class ListPosts extends AbstractFacebookTask {

    @Schema(title = "Fields", description = "Comma-separated list of fields to retrieve for each post (e.g., id,message,created_time,permalink_url)")
    protected Property<String> fields;

    @Schema(title = "Limit", description = "Maximum number of posts to retrieve", defaultValue = "1")
    protected Property<Integer> limit;

    @Override
    public Output run(RunContext runContext) throws Exception {
        String rPageId = runContext.render(this.pageId).as(String.class).orElseThrow();
        String rToken = runContext.render(this.accessToken).as(String.class).orElseThrow();

        StringBuilder urlBuilder = new StringBuilder();
        urlBuilder.append(buildApiUrl(runContext, rPageId + "/feed"));

        boolean hasParams = false;

            String rFields = runContext.render(this.fields).as(String.class).orElse(null);
            if (rFields != null && !rFields.isEmpty()) {
                urlBuilder.append("?fields=").append(rFields);
                hasParams = true;
            }

            Integer rLimit = runContext.render(this.limit).as(Integer.class).orElse(1);
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

            List<Map<String, Object>> posts = new ArrayList<>();
            if (dataArray != null && dataArray.isArray()) {
                for (JsonNode postNode : dataArray) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> post = JacksonMapper.ofJson().convertValue(postNode, Map.class);
                    posts.add(post);
                }
            }

            runContext.logger().info("Successfully retrieved {} Facebook posts", posts.size());

            return Output.builder()
                .posts(posts)
                .count(posts.size())
                .build();
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "List of posts retrieved from the Facebook Page")
        @JsonProperty("posts")
        private final List<Map<String, Object>> posts;

        @Schema(title = "Number of posts retrieved")
        @JsonProperty("count")
        private final Integer count;

    }
}