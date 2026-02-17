package io.kestra.plugin.meta.messenger;

import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.HttpResponse;
import io.kestra.core.http.client.HttpClient;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.plugin.meta.AbstractMetaConnection;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.apache.commons.io.IOUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuperBuilder
@ToString(exclude = {"accessToken"})
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public abstract class MessengerTemplate extends AbstractMetaConnection {

    @Schema(title = "Facebook Page ID", description = "Page that sends the messages; must match the access token permissions.")
    @NotNull
    protected String pageId;

    @Schema(title = "Page Access Token", description = "Page access token with pages_messaging permission for the sender Page.")
    @NotNull
    protected String accessToken;

    @Schema(title = "Recipient PSIDs", description = "Page-scoped recipient IDs; at least one is required or the task fails.")
    @NotNull
    protected Property<List<String>> recipientIds;

    @Schema(title = "Messaging type", description = "Messaging type passed to the Graph API (RESPONSE, UPDATE, MESSAGE_TAG). Defaults to UPDATE.")
    @Builder.Default
    protected Property<MessagingType> messagingType = Property.ofValue(MessagingType.UPDATE);

    @Schema(title = "Template to use", hidden = true)
    protected Property<String> templateUri;

    @Schema(title = "Template variables", description = "Values injected into the Pebble template before sending.")
    protected Property<Map<String, Object>> templateRenderMap;

    @Schema(title = "Message text body", description = "Direct message text; bypasses the template when provided.")
    protected Property<String> textBody;

    @Schema(title = "Override URL for testing", description = "Optional Graph API endpoint override; defaults to https://graph.facebook.com/v23.0/{pageId}/messages.")
    protected Property<String> url;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        final var rRecipientIds = runContext.render(this.recipientIds).asList(String.class);
        final var rAccessToken = runContext.render(this.accessToken);
        final var rPageId = runContext.render(this.pageId);
        final var rMessagingType = runContext.render(this.messagingType).as(MessagingType.class).orElse(MessagingType.UPDATE);
        final var rUrl = runContext.render(this.url).as(String.class);

        if (rRecipientIds.isEmpty()) {
            throw new IllegalArgumentException("Atleast one RecipientId is required");
        }

        String apiUrl = rUrl
                .orElseGet(() -> String.format("https://graph.facebook.com/v23.0/%s/messages?access_token=%s",
                        rPageId, rAccessToken));

        String messageText = getMessageText(runContext);

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
                        .uri(URI.create(apiUrl))
                        .method("POST")
                        .body(HttpRequest.StringRequestBody.builder().content(payload).build())
                        .build();

                HttpResponse<String> response = client.request(request, String.class);

                runContext.logger().debug("Response: {}", response.getBody());

                if (response.getStatus().getCode() == 200) {
                    runContext.logger().info("Messenger message sent successfully to {}", recipientId);
                } else {
                    runContext.logger().error("Failed to send Messenger message to {}: {}", recipientId,
                            response.getBody());
                }
            }
        }

        return null;
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
                StandardCharsets.UTF_8);

        Map<String, Object> templateVars = templateRenderMap != null
                ? runContext.render(templateRenderMap).asMap(String.class, Object.class)
                : Map.of();

        return runContext.render(template, templateVars);
    }

        return rTextBody.orElse("");

    }
}
