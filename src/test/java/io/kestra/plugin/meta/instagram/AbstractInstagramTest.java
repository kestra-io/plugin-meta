package io.kestra.plugin.meta.instagram;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.queues.QueueException;
import io.kestra.core.repositories.LocalFlowRepositoryLoader;
import io.kestra.core.runners.RunnerUtils;
import io.kestra.core.runners.TestRunner;
import io.kestra.core.tenant.TenantService;
import io.micronaut.context.ApplicationContext;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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
public abstract class AbstractInstagramTest implements TestPropertyProvider {

    @Override
    public Map<String, String> getProperties() {
        return Map.of(
                "mock.instagram.enabled", "true",
                "mock.facebook.enabled", "false");
    }

    @Inject
    protected ApplicationContext applicationContext;

    @Inject
    protected TestRunner runner;

    @Inject
    protected RunnerUtils runnerUtils;

    @Inject
    protected LocalFlowRepositoryLoader repositoryLoader;

    protected EmbeddedServer embeddedServer;

    @BeforeEach
    void setUp() throws IOException, URISyntaxException {
        repositoryLoader.load(Objects.requireNonNull(
                this.getClass().getClassLoader().getResource("flows/instagram")));
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