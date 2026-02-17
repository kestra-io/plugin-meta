package io.kestra.plugin.meta.facebook;

import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import lombok.Builder;

@SuperBuilder
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@ToString(exclude = { "accessToken" })
public abstract class AbstractFacebookTask extends Task implements RunnableTask<io.kestra.core.models.tasks.Output> {

    @Schema(title = "Facebook Page ID", description = "ID of the Page the task operates on; must match the access token scope.")
    @NotNull
    protected Property<String> pageId;

    @Schema(title = "Access Token", description = "Page access token with permissions such as `pages_manage_posts` and `pages_read_engagement`.")
    @NotNull
    protected Property<String> accessToken;

    @Schema(title = "API Version", description = "Facebook Graph API version to call. Defaults to v24.0.")
    @Builder.Default
    protected Property<String> apiVersion = Property.ofValue("v24.0");

    @Schema(title = "Base API URL", description = "Base Graph API URL. Defaults to `https://graph.facebook.com`.")
    @Builder.Default
    protected Property<String> apiBaseUrl = Property.ofValue("https://graph.facebook.com");

    protected String buildApiUrl(RunContext runContext, String endpoint) throws Exception {
        String rVersion = runContext.render(this.apiVersion).as(String.class).orElse("v24.0");
        String rBaseUrl = runContext.render(this.apiBaseUrl).as(String.class).orElse("https://graph.facebook.com");
        return String.format("%s/%s/%s", rBaseUrl, rVersion, endpoint);
    }
}
