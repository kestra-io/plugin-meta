package io.kestra.plugin.meta.instagram;

import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import io.kestra.core.models.annotations.PluginProperty;

@SuperBuilder
@Getter
@NoArgsConstructor
@ToString(exclude = { "accessToken" })
public abstract class AbstractInstagramTask extends Task implements RunnableTask<io.kestra.core.models.tasks.Output> {

    @Schema(title = "Instagram Account ID", description = "ID of the Instagram professional account to act on.")
    @NotNull
    @PluginProperty(group = "main")
    protected Property<String> igId;

    @Schema(title = "Access Token", description = "Access token with required scopes (e.g., instagram_basic, instagram_content_publish).")
    @NotNull
    @PluginProperty(group = "main", secret = true)
    protected Property<String> accessToken;

    @Schema(title = "API Version", description = "Instagram Graph API version to call. Defaults to v24.0.")
    @Builder.Default
    @PluginProperty(group = "advanced")
    protected Property<String> apiVersion = Property.ofValue("v24.0");

    @Schema(title = "Host URL", description = "Base Graph API URL. Defaults to `https://graph.facebook.com`.")
    @Builder.Default
    @PluginProperty(group = "connection")
    protected Property<String> host = Property.ofValue("https://graph.facebook.com");

    protected String buildApiUrl(RunContext runContext, String endpoint) throws Exception {
        String rVersion = runContext.render(this.apiVersion).as(String.class).orElse("v24.0");
        String rHost = runContext.render(this.host).as(String.class).orElse("https://graph.facebook.com");
        return String.format("%s/%s/%s", rHost, rVersion, endpoint);
    }
}
