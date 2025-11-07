package io.kestra.plugin.meta.facebook;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.queues.QueueException;
import io.kestra.core.repositories.LocalFlowRepositoryLoader;
import io.kestra.core.runners.TestRunnerUtils;
import io.kestra.core.runners.TestRunner;
import io.kestra.core.tenant.TenantService;
import io.micronaut.context.ApplicationContext;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeoutException;

@KestraTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractFacebookTest implements TestPropertyProvider {

    @Override
    public Map<String, String> getProperties() {
        return Map.of(
            "mock.facebook.enabled", "true",
            "mock.instagram.enabled", "false");
    }

    @Inject
    protected ApplicationContext applicationContext;

    @Inject
    protected TestRunner runner;

    @Inject
    protected TestRunnerUtils runnerUtils;

    @Inject
    protected LocalFlowRepositoryLoader repositoryLoader;

    protected EmbeddedServer embeddedServer;

    @BeforeEach
    void setUp() throws IOException, URISyntaxException {
        repositoryLoader.load(Objects.requireNonNull(
            this.getClass().getClassLoader().getResource("flows/facebook")));
        this.runner.run();

        embeddedServer = applicationContext.getBean(EmbeddedServer.class);
        embeddedServer.start();
    }

    @AfterAll
    void tearDown() {
        if (embeddedServer != null && embeddedServer.isRunning()) {
            embeddedServer.stop();
        }
    }

    protected Execution runFlow(String flowId) throws TimeoutException, QueueException {
        return runnerUtils.runOne(
            TenantService.MAIN_TENANT,
            "io.kestra.tests",
            flowId,
            null,
            (f, e) -> ImmutableMap.of("url", embeddedServer.getURI().toString()),
            Duration.ofMinutes(10));
    }
}
