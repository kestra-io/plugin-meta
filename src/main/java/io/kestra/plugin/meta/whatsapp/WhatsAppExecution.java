package io.kestra.plugin.meta.whatsapp;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.plugins.notifications.ExecutionInterface;
import io.kestra.core.plugins.notifications.ExecutionService;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Send execution summary via WhatsApp",
    description = "Sends a WhatsApp notification with the execution link, ID, namespace, flow name, start time, duration, status, and first failing task. Use in flows triggered by [Flow Triggers](https://kestra.io/docs/administrator-guide/monitoring#alerting); for `errors` tasks prefer [WhatsAppIncomingWebhook](https://kestra.io/plugins/plugin-meta/tasks/whatsapp/io.kestra.plugin.meta.whatsapp.whatsappincomingwebhook)."
)
@Plugin(
    examples = {
        @Example(
            title = "Send a WhatsApp notification on a failed flow execution.",
            full = true,
            code = """
                id: failure_alert
                namespace: company.team

                tasks:
                  - id: send_alert
                    type: io.kestra.plugin.meta.whatsapp.WhatsAppExecution
                    url: "{{ secret('WHATSAPP_WEBHOOK') }}"
                    profileName: "MyProfile"
                    from: 380999999999
                    whatsAppIds:
                        - "some waId"
                        - "waId No2"
                    executionId: "{{trigger.executionId}}"

                triggers:
                  - id: failed_prod_workflows
                    type: io.kestra.plugin.core.trigger.Flow
                    conditions:
                      - type: io.kestra.plugin.core.condition.ExecutionStatus
                        in:
                          - FAILED
                          - WARNING
                      - type: io.kestra.plugin.core.condition.ExecutionNamespace
                        namespace: prod
                        prefix: true
                """
        ),
    },
    aliases = "io.kestra.plugin.notifications.whatsapp.WhatsAppExecution"
)
public class WhatsAppExecution extends WhatsAppTemplate implements ExecutionInterface {
    @Builder.Default
    private final Property<String> executionId = Property.ofExpression("{{ execution.id }}");
    private Property<Map<String, Object>> customFields;
    private Property<String> customMessage;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        this.templateUri = Property.ofValue("whatsapp-template.peb");
        this.templateRenderMap = Property.ofValue(ExecutionService.executionMap(runContext, this));

        return super.run(runContext);
    }
}
