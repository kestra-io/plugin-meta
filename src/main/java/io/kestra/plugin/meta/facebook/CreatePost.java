package io.kestra.plugin.meta.facebook;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.HttpResponse;
import io.kestra.core.http.client.HttpClient;
import io.kestra.core.http.client.configurations.HttpConfiguration;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(title = "Create a post on a Facebook Page", description = "Publish content to a Facebook Page including text, links, and media")
@Plugin(examples = {
        @Example(title = "Create a simple text post", full = true, code = """
                id: facebook_create_post
                namespace: company.team

                tasks:
                  - id: create_post
                    type: io.kestra.plugin.meta.facebook.CreatePost
                    pageId: "{{ secret('FACEBOOK_PAGE_ID') }}"
                    accessToken: "{{ secret('FACEBOOK_ACCESS_TOKEN') }}"
                    message: "Hello from Kestra! This is an automated post."
                """),
        @Example(title = "Create a post with link", full = true, code = """
                - id: create_post_with_link
                  type: io.kestra.plugin.meta.facebook.CreatePost
                  pageId: "{{ secret('FACEBOOK_PAGE_ID') }}"
                  accessToken: "{{ secret('FACEBOOK_ACCESS_TOKEN') }}"
                  message: "Check out this amazing automation platform!"
                  link: "https://kestra.io"
                """)
})
public class CreatePost extends AbstractFacebookTask {

    @Schema(title = "Post Message", description = "The text content of the post")
    protected Property<String> message;

    @Schema(title = "Link URL", description = "Optional link to include in the post")
    protected Property<String> link;

    @Schema(title = "Publish Immediately", description = "Whether to publish the post immediately or save as draft", defaultValue = "true")
    protected Property<Boolean> published;

    @Override
    public Output run(RunContext runContext) throws Exception {
        String rPageId = runContext.render(this.pageId).as(String.class).orElseThrow();
        String rToken = runContext.render(this.accessToken).as(String.class).orElseThrow();
        String url = buildApiUrl(runContext, rPageId + "/feed");

        Map<String, Object> postData = new HashMap<>();

        if (this.message != null) {
            runContext.render(this.message).as(String.class).ifPresent(msg -> postData.put("message", msg));
        }

        if (this.link != null) {
            runContext.render(this.link).as(String.class).ifPresent(linkUrl -> postData.put("link", linkUrl));
        }

        Boolean isPublished = runContext.render(this.published).as(Boolean.class).orElse(true);
        postData.put("published", isPublished);

        String jsonBody = JacksonMapper.ofJson().writeValueAsString(postData);
        String fullUrl = url + (url.contains("?") ? "&" : "?") + "access_token=" + URLEncoder.encode(rToken, StandardCharsets.UTF_8);

        HttpRequest request = HttpRequest.builder()
                .uri(URI.create(fullUrl))
                .method("POST")
                .body(HttpRequest.StringRequestBody.builder()
                        .content(jsonBody)
                        .contentType("application/json")
                        .build())
                .build();

        HttpConfiguration httpConfiguration = HttpConfiguration.builder().build();

        try (HttpClient httpClient = HttpClient.builder()
                .runContext(runContext)
                .configuration(httpConfiguration)
                .build()) {
            HttpResponse<String> response = httpClient.request(request, String.class);

            if (response.getStatus().getCode() != 200) {
                throw new RuntimeException(
                        "Failed to create post: " + response.getStatus().getCode() + " - " + response.getBody());
            }

            JsonNode responseJson = JacksonMapper.ofJson().readTree(response.getBody());
            String postId = responseJson.get("id").asText();

            runContext.logger().info("Successfully created Facebook post with ID: {}", postId);

            return Output.builder()
                    .postId(postId)
                    .message(postData.get("message") != null ? postData.get("message").toString() : null)
                    .link(postData.get("link") != null ? postData.get("link").toString() : null)
                    .published(isPublished)
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

        @Schema(title = "Whether the post was published immediately")
        @JsonProperty("published")
        private final Boolean published;
    }
}