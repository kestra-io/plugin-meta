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

        String receivedData = waitForWebhookData(
            () -> MockMessengerApiServer.bodies.isEmpty() ? null : MockMessengerApiServer.bodies.getFirst(),
            5000
        );

        var decoded = java.net.URLDecoder.decode(receivedData, java.nio.charset.StandardCharsets.UTF_8);
        assertThat(decoded, containsString("24745216345137108"));
        assertThat(decoded, containsString(execution.getId()));
        assertThat(decoded, containsString("Sent through the SDK"));
    }

    /** The SDK cannot express timeouts or custom headers, so options must keep the pre-SDK request. */
    @Test
    void flow_optionsKeepsThePreSdkRequest() throws Exception {
        var execution = runAndCaptureExecution(
            "main-flow-that-succeeds",
            "messenger-options"
        );

        waitForWebhookData(() -> FakeWebhookController.data, 5000);

        // the other messenger flows share the upstream trigger, so identify this one by its own message
        assertThat(
            MockMessengerApiServer.bodies.stream().noneMatch(b -> b.contains("Options+keeps+the+pre-SDK+path")),
            is(true)
        );
        assertThat(execution.getState().getCurrent().isSuccess(), is(true));
    }
}
