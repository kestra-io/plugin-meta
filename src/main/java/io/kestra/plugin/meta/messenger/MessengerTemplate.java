package io.kestra.plugin.meta.messenger;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;

import com.facebook.ads.sdk.APIContext;
import com.facebook.ads.sdk.APIException;
import com.facebook.ads.sdk.Page;

import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.HttpResponse;
import io.kestra.core.http.client.HttpClient;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.plugin.meta.AbstractMetaConnection;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@ToString(exclude = { "accessToken" })
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public abstract class MessengerTemplate extends AbstractMetaConnection {

    @Schema(title = "Facebook Page ID", description = "Page that sends the messages; must match the access token permissions.")
    @NotNull
    @PluginProperty(group = "main")
    protected String pageId;

    @Schema(title = "Page Access Token", description = "Page access token with pages_messaging permission for the sender Page.")
    @NotNull
    @PluginProperty(group = "main", secret = true)
    protected Property<String> accessToken;

    @Schema(title = "Recipient PSIDs", description = "Page-scoped recipient IDs; at least one is required or the task fails.")
    @NotNull
    @PluginProperty(group = "main")
    protected Property<List<String>> recipientIds;

    @Schema(title = "Messaging type", description = "Messaging type passed to the Graph API (RESPONSE, UPDATE, MESSAGE_TAG). Defaults to UPDATE.")
    @Builder.Default
    @PluginProperty(group = "advanced")
    protected Property<MessagingType> messagingType = Property.ofValue(MessagingType.UPDATE);

    @Schema(title = "Template to use", hidden = true)
    @PluginProperty(group = "advanced")
    protected Property<String> templateUri;

    @Schema(title = "Template variables", description = "Values injected into the Pebble template before sending.")
    @PluginProperty(group = "advanced")
    protected Property<Map<String, Object>> templateRenderMap;

    @Schema(title = "Message text body", description = "Direct message text; bypasses the template when provided.")
    @PluginProperty(group = "advanced")
    protected Property<String> textBody;

    @Schema(
        title = "Override URL for testing",
        description = "Optional Graph API endpoint override; defaults to https://graph.facebook.com/v23.0/{pageId}/messages. When set, or when `options` is set, the request is posted directly instead of through the SDK."
    )
    @PluginProperty(group = "connection")
    protected Property<String> url;

    @Schema(title = "API Version", description = "Graph API version to call. Defaults to v23.0.")
    @Builder.Default
    @PluginProperty(group = "advanced")
    protected Property<String> apiVersion = Property.ofValue("v23.0");

    @Schema(title = "Base API URL", description = "Base Graph API URL. Defaults to `https://graph.facebook.com`.")
    @Builder.Default
    @PluginProperty(group = "connection")
    protected Property<String> apiBaseUrl = Property.ofValue("https://graph.facebook.com");

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        final var rRecipientIds = runContext.render(this.recipientIds).asList(String.class);
        final var rAccessToken = runContext.render(this.accessToken).as(String.class).orElseThrow();
        final var rPageId = runContext.render(this.pageId);
        final var rMessagingType = runContext.render(this.messagingType).as(MessagingType.class).orElse(MessagingType.UPDATE);
        final var rUrl = runContext.render(this.url).as(String.class);

        if (rRecipientIds.isEmpty()) {
            throw new IllegalArgumentException("Atleast one RecipientId is required");
        }

        String messageText = getMessageText(runContext);

        // the SDK has no seam for timeouts or custom headers, so options keeps the caller on the pre-SDK request
        if (rUrl.isEmpty() && options == null) {
            sendThroughSdk(runContext, rPageId, rRecipientIds, rMessagingType, messageText);

            return null;
        }

        // apiBaseUrl and apiVersion must drive the fallback too, or a configured base is bypassed for real Facebook
        String rBaseUrl = runContext.render(this.apiBaseUrl).as(String.class).orElse("https://graph.facebook.com");
        String rVersion = runContext.render(this.apiVersion).as(String.class).orElse("v23.0");
        String apiUrl = rUrl.orElseGet(() -> "%s/%s/%s/messages".formatted(rBaseUrl, rVersion, rPageId));

        try (HttpClient client = new HttpClient(runContext, super.httpClientConfigurationWithOptions())) {
            for (String recipientId : rRecipientIds) {
                Map<String, Object> messagePayload = new HashMap<>();
                messagePayload.put("recipient", Map.of("id", recipientId));
                messagePayload.put("messaging_type", rMessagingType);
                messagePayload.put("message", Map.of("text", messageText));

                String payload = JacksonMapper.ofJson().writeValueAsString(messagePayload);

                runContext.logger().debug("Sending Messenger message to {}", recipientId);

                HttpRequest request = createRequestBuilder(runContext)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Authorization", "Bearer " + rAccessToken)
                    .uri(URI.create(apiUrl))
                    .method("POST")
                    .body(HttpRequest.StringRequestBody.builder().content(payload).build())
                    .build();

                HttpResponse<String> response = client.request(request, String.class);

                runContext.logger().debug("Response: {}", response.getBody());

                if (response.getStatus().getCode() == 200) {
                    runContext.logger().info("Messenger message sent successfully to {}", recipientId);
                } else {
                    runContext.logger().error(
                        "Failed to send Messenger message to {}: {}", recipientId,
                        response.getBody()
                    );
                }
            }
        }

        return null;
    }

    /** The url override stays on the verbatim POST, since an arbitrary endpoint has no SDK equivalent. */
    private void sendThroughSdk(RunContext runContext, String pageId, List<String> recipientIds,
        MessagingType messagingType, String messageText) throws Exception {
        var context = apiContext(runContext);

        for (String recipientId : recipientIds) {
            runContext.logger().debug("Sending Messenger message to {}", recipientId);

            try {
                new Page(pageId, context)
                    .createMessage()
                    .setRecipient(JacksonMapper.ofJson().writeValueAsString(Map.of("id", recipientId)))
                    .setMessagingType(messagingType.name())
                    .setMessage(JacksonMapper.ofJson().writeValueAsString(Map.of("text", messageText)))
                    .execute();

                runContext.logger().info("Messenger message sent successfully to {}", recipientId);
            } catch (APIException e) {
                runContext.logger().error("Failed to send Messenger message to {}: {}", recipientId, e.getMessage());
            }
        }
    }

    /** The seven argument constructor is the only seam for the base URL, which apiBaseUrl controls. */
    private APIContext apiContext(RunContext runContext) throws Exception {
        var rToken = runContext.render(this.accessToken).as(String.class).orElseThrow();
        var rVersion = runContext.render(this.apiVersion).as(String.class).orElse("v23.0");
        var rBaseUrl = runContext.render(this.apiBaseUrl).as(String.class).orElse("https://graph.facebook.com");

        return new APIContext(rBaseUrl, rBaseUrl, rVersion, rToken, null, null, false);
    }

    private String getMessageText(RunContext runContext) throws Exception {
        final var rTextBody = runContext.render(this.textBody).as(String.class);
        final var rTemplateUri = runContext.render(this.templateUri).as(String.class);

        if (rTemplateUri.isPresent()) {
            var resourceStream = this.getClass().getClassLoader().getResourceAsStream(rTemplateUri.get());
            if (resourceStream == null) {
                throw new IllegalArgumentException("Template resource not found: " + rTemplateUri.get());
            }
            String template = IOUtils.toString(
                resourceStream,
                StandardCharsets.UTF_8
            );

            Map<String, Object> templateVars = templateRenderMap != null
                ? runContext.render(templateRenderMap).asMap(String.class, Object.class)
                : Map.of();

            return runContext.render(template, templateVars);
        }

        return rTextBody.orElse("");

    }
}
