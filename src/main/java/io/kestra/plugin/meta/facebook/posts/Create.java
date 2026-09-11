package io.kestra.plugin.meta.facebook.posts;

import com.facebook.ads.sdk.APIException;
import com.facebook.ads.sdk.Page;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.meta.facebook.AbstractFacebookTask;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import io.kestra.core.models.annotations.PluginProperty;

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
            full = true,
            code = """
                id: create_facebook_post
                namespace: company.team

                tasks:
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
    @PluginProperty(group = "main")
    protected Property<String> message;

    @Schema(title = "Link URL", description = "Optional HTTP/HTTPS link to attach to the post.")
    @PluginProperty(group = "advanced")
    protected Property<String> link;

    @Override
    public Output run(RunContext runContext) throws Exception {
        String rPageId = runContext.render(this.pageId).as(String.class).orElseThrow();
        String rMessage = runContext.render(this.message).as(String.class).orElseThrow();

        var request = new Page(rPageId, apiContext(runContext))
            .createFeed()
            .setMessage(rMessage)
            .setPublished(Boolean.TRUE);

        runContext.render(this.link).as(String.class).ifPresent(request::setLink);

        String postId;
        try {
            postId = request.execute().getId();
        } catch (APIException e) {
            throw new RuntimeException("Failed to create post: %s".formatted(e.getMessage()), e);
        }

        if (postId == null) {
            throw new RuntimeException("Facebook returned no post id for page %s".formatted(rPageId));
        }

        runContext.logger().info("Successfully created Facebook post with ID: {}", postId);

        return Output.builder()
            .postId(postId)
            .build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "ID of the created post")
        @JsonProperty("postId")
        private final String postId;
    }
}
