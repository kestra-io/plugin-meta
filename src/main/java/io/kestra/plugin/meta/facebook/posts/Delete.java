package io.kestra.plugin.meta.facebook.posts;

import java.util.ArrayList;
import java.util.List;

import com.facebook.ads.sdk.PagePost;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.plugin.meta.facebook.AbstractFacebookTask;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@NoArgsConstructor
@Getter
@ToString
@EqualsAndHashCode
@Schema(
    title = "Delete Facebook Page posts",
    description = "Deletes one or more Page posts by ID (pageId_postId format). Continues through the list and reports successes and failures."
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
                    type: io.kestra.plugin.meta.facebook.posts.Delete
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
                id: delete_facebook_posts
                namespace: company.team

                tasks:
                  - id: delete_multiple_posts
                    type: io.kestra.plugin.meta.facebook.posts.Delete
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
public class Delete extends AbstractFacebookTask {

    @Schema(title = "Post IDs", description = "Post identifiers in pageId_postId format to delete.")
    @NotNull
    protected Property<java.util.List<String>> postIds;

    @Override
    public Output run(RunContext runContext) throws Exception {
        List<String> rPostIds = runContext.render(this.postIds).asList(String.class);
        var context = apiContext(runContext);

        java.util.List<String> deletedPostIds = new ArrayList<>();
        java.util.List<String> failedPostIds = new ArrayList<>();

        // one failure must not stop the rest, the outputs report both lists
        for (String postId : rPostIds) {
            try {
                // Graph answers 200 with success:false for a post it would not delete, so the body still decides
                var response = new PagePost(postId, context).delete().execute();
                JsonNode responseJson = JacksonMapper.ofJson().readTree(response.getRawResponse());
                JsonNode successNode = responseJson.get("success");

                if (successNode == null || !successNode.asBoolean()) {
                    runContext.logger().error("Facebook API returned success: false for post deletion: {}", postId);
                    failedPostIds.add(postId);
                    continue;
                }

                runContext.logger().info("Successfully deleted Facebook post with ID: {}", postId);
                deletedPostIds.add(postId);
            } catch (Exception e) {
                runContext.logger().error("Error deleting post {}: {}", postId, e.getMessage(), e);
                failedPostIds.add(postId);
            }
        }

        return Output.builder()
            .deletedPostIds(deletedPostIds)
            .failedPostIds(failedPostIds)
            .totalDeleted(deletedPostIds.size())
            .totalFailed(failedPostIds.size())
            .allSuccess(failedPostIds.isEmpty())
            .build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "Deleted post IDs")
        @JsonProperty("deletedPostIds")
        private final java.util.List<String> deletedPostIds;

        @Schema(title = "Failed post IDs")
        @JsonProperty("failedPostIds")
        private final java.util.List<String> failedPostIds;

        @Schema(title = "Total posts deleted")
        @JsonProperty("totalDeleted")
        private final Integer totalDeleted;

        @Schema(title = "Total posts failed")
        @JsonProperty("totalFailed")
        private final Integer totalFailed;

        @Schema(title = "All deletions succeeded")
        @JsonProperty("allSuccess")
        private final Boolean allSuccess;
    }
}
