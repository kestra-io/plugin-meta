package io.kestra.plugin.meta.whatsapp;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.junit.jupiter.api.Test;

import com.google.common.io.Files;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.validations.ModelValidator;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.serializers.YamlParser;
import io.kestra.core.utils.IdUtils;
import io.kestra.plugin.meta.FakeWebhookController;

import io.micronaut.context.ApplicationContext;
import io.micronaut.runtime.server.EmbeddedServer;
import jakarta.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@KestraTest
public class WhatsAppIncomingWebhookTest {

    @Inject
    private ApplicationContext applicationContext;

    @Inject
    private RunContextFactory runContextFactory;

    @Inject
    private ModelValidator modelValidator;

    @Test
    void run() throws Exception {
        RunContext runContext = runContextFactory.of(
            Map.of(
                "textBody", "WhatsApp test webhook notification. A message *with some bold text* and _some italicized text_.",
                "whatsAppIds", List.of("someId", "secondId")
            )
        );

        EmbeddedServer embeddedServer = applicationContext.getBean(EmbeddedServer.class);
        embeddedServer.start();

        WhatsAppIncomingWebhook task = WhatsAppIncomingWebhook.builder()
            .url(embeddedServer.getURI() + "/webhook-unit-test")
            .payload(
                Property.ofExpression(
                    Files.asCharSource(
                        new File(
                            Objects.requireNonNull(
                                WhatsAppIncomingWebhookTest.class.getClassLoader()
                                    .getResource("whatsapp.peb")
                            )
                                .toURI()
                        ),
                        StandardCharsets.UTF_8
                    ).read()
                )
            )
            .build();

        task.run(runContext);

        assertThat(FakeWebhookController.bodyContaining("ge *with some bold text* an"), notNullValue());
    }

    @SuppressWarnings("deprecation")
    @Test
    void flowSettingDeprecatedOptionsStillValidatesAndRuns() throws Exception {
        // Flows written before the deprecation still set these options: they must keep parsing, validating and running.
        var message = "deprecated-options-" + IdUtils.create();
        var embeddedServer = applicationContext.getBean(EmbeddedServer.class);
        embeddedServer.start();

        var flow = YamlParser.parse(
            """
                id: whatsapp_deprecated_options
                namespace: io.kestra.tests
                tasks:
                  - id: send
                    type: io.kestra.plugin.meta.whatsapp.WhatsAppIncomingWebhook
                    url: "%s/webhook-unit-test"
                    payload: '{"textBody": "%s"}'
                    options:
                      readTimeout: PT30S
                      connectionPoolIdleTimeout: PT10S
                      maxContentLength: 1024
                """.formatted(embeddedServer.getURI(), message),
            Flow.class
        );

        assertThat(modelValidator.isValid(flow).isEmpty(), is(true));

        var runContext = runContextFactory.of(Map.of());
        var task = (WhatsAppIncomingWebhook) flow.getTasks().getFirst();
        var options = task.getOptions();
        assertThat(runContext.render(options.getReadTimeout()).as(Duration.class).orElseThrow(), is(Duration.ofSeconds(30)));
        assertThat(runContext.render(options.getConnectionPoolIdleTimeout()).as(Duration.class).orElseThrow(), is(Duration.ofSeconds(10)));
        assertThat(runContext.render(options.getMaxContentLength()).as(Integer.class).orElseThrow(), is(1024));

        task.run(runContext);

        assertThat(FakeWebhookController.bodyContaining(message), notNullValue());
    }

}
