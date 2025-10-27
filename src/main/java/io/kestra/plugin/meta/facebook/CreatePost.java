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
    title = "Create a post on a Facebook Page",
    description = "Publish content to a Facebook Page including text, links, and media"
)
@Plugin(
    examples = {
        @Example(
            title = "Create a simple text post",
            full = true,
            code = """
                id: facebook_create_post
                namespace: company.team

                tasks:
                  - id: create_post
                    type: io.kestra.plugin.meta.facebook.CreatePost
                    pageId: "{{ secret('FACEBOOK_PAGE_ID') }}"
                    accessToken: "{{ secret('FACEBOOK_ACCESS_TOKEN') }}"
                    message: "Hello from Kestra! This is an automated post."
                """
        ),
        @Example(
            title = "Create a post with link",
            full = true,
            code = """
                - id: create_post_with_link
                  type: io.kestra.plugin.meta.facebook.CreatePost
                  pageId: "{{ secret('FACEBOOK_PAGE_ID') }}"
                  accessToken: "{{ secret('FACEBOOK_ACCESS_TOKEN') }}"
                  message: "Check out this amazing automation platform!"
                  link: "https://kestra.io"
                """
        )
    }
)
public class CreatePost extends AbstractFacebookTask {

    @Schema(title = "Post Message", description = "The text content of the post")
    protected Property<String> message;

    @Schema(title = "Link URL", description = "Optional link to include in the post")
    protected Property<String> link;

    @Override
    public Output run(RunContext runContext) throws Exception {
        String rPageId = runContext.render(this.pageId).as(String.class).orElseThrow();
        String rToken = runContext.render(this.accessToken).as(String.class).orElseThrow();
        String url = buildApiUrl(runContext, rPageId + "/feed");

        Map<String, Object> postData = new HashMap<>();

        runContext.render(this.message).as(String.class).ifPresent(msg -> postData.put("message", msg));
        runContext.render(this.link).as(String.class).ifPresent(linkUrl -> postData.put("link", linkUrl));

        postData.put("published", Boolean.TRUE);

        String jsonBody = JacksonMapper.ofJson().writeValueAsString(postData);

        HttpRequest request = HttpRequest.builder()
            .method("POST")
            .uri(URI.create(url))
            .body(HttpRequest.StringRequestBody.builder()
                .content(jsonBody)
                .build())
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer " + rToken)
            .build();


        try (HttpClient httpClient = HttpClient.builder()
            .runContext(runContext)
            .build()) {
            HttpResponse<String> response = httpClient.request(request, String.class);

            int statusCode = response.getStatus().getCode();
            if (statusCode < 200 || statusCode >= 300) {
                throw new RuntimeException(
                    "Failed to create post: " + statusCode + " - " + response.getBody());
            }

            JsonNode responseJson = JacksonMapper.ofJson().readTree(response.getBody());
            JsonNode idNode = responseJson.get("id");
            if (idNode == null) {
                throw new RuntimeException("Response missing 'id' field: " + response.getBody());
            }
            String postId = idNode.asText();
            runContext.logger().info("Successfully created Facebook post with ID: {}", postId);

            return Output.builder()
                .postId(postId)
                .message(postData.get("message") != null ? postData.get("message").toString() : null)
                .link(postData.get("link") != null ? postData.get("link").toString() : null)
                .build();
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "The ID of the created post")
        @JsonProperty("postId")
        private final String postId;

        @Schema(title = "The message content of the post")
        @JsonProperty("message")
        private final String message;

        @Schema(title = "The link included in the post")
        @JsonProperty("link")
        private final String link;

    }
}