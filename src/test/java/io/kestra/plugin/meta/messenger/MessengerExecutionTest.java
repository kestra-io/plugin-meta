package io.kestra.plugin.meta.messenger;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Objects;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.repositories.LocalFlowRepositoryLoader;
import io.kestra.core.runners.TestRunner;
import io.kestra.plugin.meta.AbstractMetaTest;
import io.kestra.plugin.meta.FakeWebhookController;

import jakarta.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

@KestraTest
public class MessengerExecutionTest extends AbstractMetaTest {

    @Inject
    protected TestRunner runner;

    @Inject
    protected LocalFlowRepositoryLoader repositoryLoader;

    @BeforeEach
    protected void init() throws IOException, URISyntaxException {
        repositoryLoader.load(Objects.requireNonNull(MessengerExecutionTest.class.getClassLoader().getResource("flows/common")));
        repositoryLoader.load(Objects.requireNonNull(MessengerExecutionTest.class.getClassLoader().getResource("flows/messenger")));
        this.runner.run();

        io.kestra.plugin.meta.facebook.PlainHttpRequestExecutor.install();
        MockMessengerApiServer.bodies.clear();
    }

    @Test
    void flow() throws Exception {
        var execution = runAndCaptureExecution(
            "main-flow-that-fails",
            "messenger"
        );

        String receivedData = waitForWebhookData(() -> FakeWebhookController.data, 5000);

        assertThat(receivedData, containsString(execution.getId()));
        assertThat(receivedData, containsString("https://mysuperhost.com/kestra/ui"));
        assertThat(receivedData, containsString("Failed on task `failed`"));
        assertThat(receivedData, containsString("\"recipient\":{\"id\":\"24745216345137108\"}"));
        assertThat(receivedData, containsString("Environment: DEV"));
        assertThat(receivedData, containsString("Cloud: GCP"));
        assertThat(receivedData, containsString("myCustomMessage"));
    }

    @Test
    void flow_successfulFlowShowLastTaskId() throws Exception {
        var execution = runAndCaptureExecution(
            "main-flow-that-succeeds",
            "messenger-successful"
        );

        String receivedData = waitForWebhookData(() -> FakeWebhookController.data, 5000);

        assertThat(receivedData, containsString(execution.getId()));
        assertThat(receivedData, containsString("https://mysuperhost.com/kestra/ui"));
        assertThat(receivedData, not(containsString("Failed Task:")));
        assertThat(receivedData, containsString("SUCCESS"));
        assertThat(receivedData, containsString("Environment: DEV"));
        assertThat(receivedData, containsString("Status: SUCCESS"));
        assertThat(receivedData, containsString("\"recipient\":{\"id\":\"24745216345137108\"}"));
    }

    /** Every other messenger flow sets url, so this is the only coverage of the SDK path. */
    @Test
    void flow_sendsThroughTheSdkWhenOnlyApiBaseUrlIsSet() throws Exception {
        var execution = runAndCaptureExecution(
            "main-flow-that-succeeds",
            "messenger-sdk"
        );

        // the messenger flows share an upstream trigger and CI runs them concurrently, so find this flow's own body
        var body = waitForWebhookData(() -> bodyContaining("Sent+through+the+SDK"), 5000);
        var decoded = java.net.URLDecoder.decode(body, java.nio.charset.StandardCharsets.UTF_8);

        assertThat(decoded, containsString("24745216345137108"));
        assertThat(decoded, containsString(execution.getId()));
        // the SDK posts form encoded, which is what marks this as the SDK path
        assertThat(body.startsWith("{"), is(false));
    }

    /** The SDK cannot express timeouts or custom headers, so options must keep the pre-SDK request. */
    @Test
    void flow_optionsKeepsThePreSdkRequest() throws Exception {
        var execution = runAndCaptureExecution(
            "main-flow-that-succeeds",
            "messenger-options"
        );

        // both paths hit the same mock route, so the body shape is what tells them apart: the SDK posts form
        // encoded, the pre-SDK path posts JSON
        var body = waitForWebhookData(() -> bodyContaining("Options keeps the pre-SDK path"), 5000);

        assertThat(body.trim().startsWith("{"), is(true));
        assertThat(body, containsString("\"messaging_type\""));
        assertThat(execution.getState().getCurrent().isSuccess(), is(true));
    }

    /** Null until a body carrying this marker arrives, so concurrent flows cannot cross-contaminate. */
    private static String bodyContaining(String marker) {
        return MockMessengerApiServer.bodies.stream()
            .filter(b -> b.contains(marker))
            .findFirst()
            .orElse(null);
    }
}
