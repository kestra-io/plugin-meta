package io.kestra.plugin.meta.instagram;

import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import lombok.Builder;

@SuperBuilder
@Getter
@NoArgsConstructor
@ToString(exclude = { "accessToken" })
public abstract class AbstractInstagramTask extends Task implements RunnableTask<io.kestra.core.models.tasks.Output> {

    @Schema(title = "Instagram Account ID", description = "The ID of the Instagram professional account")
    @NotNull
    protected Property<String> igId;

    @Schema(title = "Access Token", description = "Instagram access token with appropriate permissions (instagram_basic, instagram_content_publish, etc.)")
    @NotNull
    protected Property<String> accessToken;

    @Schema(title = "API Version", description = "Instagram Graph API version to use")
    @Builder.Default
    protected Property<String> apiVersion = Property.ofValue("v24.0");

    @Schema(title = "Host URL", description = "The host URL for the Instagram Graph API")
    @Builder.Default
    protected Property<String> host = Property.ofValue("https://graph.facebook.com");

    protected String buildApiUrl(RunContext runContext, String endpoint) throws Exception {
        String rVersion = runContext.render(this.apiVersion).as(String.class).orElse("v24.0");
        String rHost = runContext.render(this.host).as(String.class).orElse("https://graph.facebook.com");
        return String.format("%s/%s/%s", rHost, rVersion, endpoint);
    }
}