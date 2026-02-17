package io.kestra.plugin.meta.facebook.posts;

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
import io.kestra.plugin.meta.facebook.AbstractFacebookTask;
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
    title = "Publish a Facebook Page post",
    description = "Publishes a post to the Page feed with required message text and an optional link. Requires a Page access token with publish permissions."
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
                    type: io.kestra.plugin.meta.facebook.posts.Create
                    pageId: "{{ secret('FACEBOOK_PAGE_ID') }}"
                    accessToken: "{{ secret('FACEBOOK_ACCESS_TOKEN') }}"
                    message: "Hello from Kestra! This is an automated post."
                """
        ),
        @Example(
            title = "Create a post with link",
            code = """
                - id: create_post_with_link
                  type: io.kestra.plugin.meta.facebook.posts.Create
                  pageId: "{{ secret('FACEBOOK_PAGE_ID') }}"
                  accessToken: "{{ secret('FACEBOOK_ACCESS_TOKEN') }}"
                  message: "Check out this amazing automation platform!"
                  link: "https://kestra.io"
                """
        )
    }
)
public class Create extends AbstractFacebookTask {

    @Schema(title = "Post message", description = "Text content to publish to the Page feed.")
    @NotNull
    protected Property<String> message;

    @Schema(title = "Link URL", description = "Optional HTTP/HTTPS link to attach to the post.")
    protected Property<String> link;

    @Override
    public Output run(RunContext runContext) throws Exception {
        String rPageId = runContext.render(this.pageId).as(String.class).orElseThrow();
        String rToken = runContext.render(this.accessToken).as(String.class).orElseThrow();
        String url = buildApiUrl(runContext, rPageId + "/feed");

        Map<String, Object> postData = new HashMap<>();

        String rMessage = runContext.render(this.message).as(String.class).orElseThrow();
        postData.put("message", rMessage);

        runContext.render(this.link).as(String.class).ifPresent(rLinkUrl -> postData.put("link", rLinkUrl));

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
                .build();
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "ID of the created post")
        @JsonProperty("postId")
        private final String postId;
    }
}
