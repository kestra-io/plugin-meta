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
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import lombok.Builder;
import jakarta.validation.constraints.NotNull;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@SuperBuilder
@NoArgsConstructor
@Getter
@ToString
@EqualsAndHashCode
@Schema(
    title = "Delete a post from a Facebook Page",
    description = "Delete a specific post from a Facebook Page using its post ID"
)
@Plugin(
    examples = {
        @Example(
            title = "Delete a Facebook post",
            full = true,
            code = """
                id: facebook_delete_post
                namespace: company.team

                tasks:
                  - id: delete_post
                    type: io.kestra.plugin.meta.facebook.DeletePost
                    pageId: "{{ secret('FACEBOOK_PAGE_ID') }}"
                    accessToken: "{{ secret('FACEBOOK_ACCESS_TOKEN') }}"
                    postIds:
                      - "123456789_987654321"
                """
        ),
        @Example(
            title = "Delete multiple Facebook posts",
            full = true,
            code = """
                - id: delete_multiple_posts
                  type: io.kestra.plugin.meta.facebook.DeletePost
                  pageId: "{{ secret('FACEBOOK_PAGE_ID') }}"
                  accessToken: "{{ secret('FACEBOOK_ACCESS_TOKEN') }}"
                  postIds:
                    - "123456789_987654321"
                    - "123456789_987654322"
                    - "123456789_987654323"
                """
        )
    }
)
public class DeletePost extends AbstractFacebookTask {

    @Schema(title = "Post IDs", description = "List of post IDs to delete (format: pageId_postId)")
    @NotNull
    protected Property<java.util.List<String>> postIds;

    @Override
    public Output run(RunContext runContext) throws Exception {
        java.util.List<String> rPostIds = runContext.render(this.postIds).asList(String.class);
        String rToken = runContext.render(this.accessToken).as(String.class).orElseThrow();

        java.util.List<String> deletedPostIds = new java.util.ArrayList<>();
        java.util.List<String> failedPostIds = new java.util.ArrayList<>();

        HttpConfiguration httpConfiguration = HttpConfiguration.builder().build();

        try (HttpClient httpClient = HttpClient.builder()
                .runContext(runContext)
                .configuration(httpConfiguration)
                .build()) {

            for (String postId : rPostIds) {
                try {
                    String url = buildApiUrl(runContext, postId);
                    String fullUrl = url + (url.contains("?") ? "&" : "?") + "access_token="
                            + URLEncoder.encode(rToken, StandardCharsets.UTF_8);

                    HttpRequest request = HttpRequest.builder()
                            .uri(URI.create(fullUrl))
                            .method("DELETE")
                            .build();

                    HttpResponse<String> response = httpClient.request(request, String.class);

                    if (response.getStatus().getCode() != 200) {
                        runContext.logger().error("Failed to delete post {}: {} - {}", postId,
                                response.getStatus().getCode(), response.getBody());
                        failedPostIds.add(postId);
                        continue;
                    }

                    JsonNode responseJson = JacksonMapper.ofJson().readTree(response.getBody());
                    boolean success = responseJson.get("success").asBoolean();

                    if (!success) {
                        runContext.logger().error("Facebook API returned success: false for post deletion: {}", postId);
                        failedPostIds.add(postId);
                    } else {
                        runContext.logger().info("Successfully deleted Facebook post with ID: {}", postId);
                        deletedPostIds.add(postId);
                    }
                } catch (Exception e) {
                    runContext.logger().error("Error deleting post {}: {}", postId, e.getMessage(), e);
                    failedPostIds.add(postId);
                }
            }

            boolean allSuccess = failedPostIds.isEmpty();

            return Output.builder()
                    .deletedPostIds(deletedPostIds)
                    .failedPostIds(failedPostIds)
                    .totalDeleted(deletedPostIds.size())
                    .totalFailed(failedPostIds.size())
                    .allSuccess(allSuccess)
                    .build();
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "List of successfully deleted post IDs")
        @JsonProperty("deletedPostIds")
        private final java.util.List<String> deletedPostIds;

        @Schema(title = "List of post IDs that failed to delete")
        @JsonProperty("failedPostIds")
        private final java.util.List<String> failedPostIds;

        @Schema(title = "Total number of posts deleted")
        @JsonProperty("totalDeleted")
        private final Integer totalDeleted;

        @Schema(title = "Total number of posts that failed to delete")
        @JsonProperty("totalFailed")
        private final Integer totalFailed;

        @Schema(title = "Whether all posts were successfully deleted")
        @JsonProperty("allSuccess")
        private final Boolean allSuccess;
    }
}