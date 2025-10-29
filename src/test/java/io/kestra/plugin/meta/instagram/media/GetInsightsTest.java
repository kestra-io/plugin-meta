package io.kestra.plugin.meta.instagram.media;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.property.Property;
import io.kestra.core.queues.QueueException;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.plugin.meta.instagram.AbstractInstagramTest;
import io.kestra.plugin.meta.instagram.enums.InsightMetric;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@KestraTest
class GetInsightsTest extends AbstractInstagramTest {

    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void getInsightsSuccess() throws TimeoutException, QueueException {
        Execution execution = runFlow("instagram_get_insights_test");

        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.getTaskRunList(), hasSize(1));
        assertThat(execution.getTaskRunList().getFirst().getOutputs().get("mediaId"), notNullValue());
        assertThat(execution.getTaskRunList().getFirst().getOutputs().get("mediaId"), is("17954170374002653"));
        assertThat(execution.getTaskRunList().getFirst().getOutputs().get("totalInsights"), notNullValue());

        @SuppressWarnings("unchecked")
        List<Object> insights = (List<Object>) execution.getTaskRunList().getFirst().getOutputs().get("insights");
        assertThat(insights, notNullValue());
        assertThat(insights.size(), greaterThan(0));
    }

    @Test
    void getInsightsWithSpecificMetrics() throws Exception {
        RunContext runContext = runContextFactory.of();

        GetInsights task = GetInsights.builder()
            .host(Property.ofValue(embeddedServer.getURL().toString()))
            .igId(Property.ofValue("mock-ig-id"))
            .accessToken(Property.ofValue("mock-access-token"))
            .mediaId(Property.ofValue("17954170374002653"))
            .metrics(Property.ofValue(List.of(
                InsightMetric.IMPRESSIONS,
                InsightMetric.REACH,
                InsightMetric.SAVES)))
            .build();

        GetInsights.Output output = task.run(runContext);

        assertThat(output, notNullValue());
        assertThat(output.getInsights(), notNullValue());
        assertThat(output.getInsights().size(), greaterThan(0));
    }
}