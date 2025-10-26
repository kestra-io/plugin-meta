package io.kestra.plugin.meta.instagram;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@KestraTest
class GetMediaInsightsTest extends AbstractInstagramTest {

    @Test
    void getMediaInsights() throws TimeoutException, QueueException {
        Execution execution = runFlow("instagram_get_media_insights_test");

        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.getTaskRunList(), hasSize(1));
        assertThat(execution.getTaskRunList().getFirst().getOutputs().get("mediaId"), notNullValue());
        assertThat(execution.getTaskRunList().getFirst().getOutputs().get("mediaId"), is("17954170374002653"));
        assertThat(execution.getTaskRunList().getFirst().getOutputs().get("totalInsights"), notNullValue());
        assertThat(execution.getTaskRunList().getFirst().getOutputs().get("totalInsights"), is(1));

        @SuppressWarnings("unchecked")
        List<Object> insights = (List<Object>) execution.getTaskRunList().getFirst().getOutputs().get("insights");
        assertThat(insights, hasSize(1));
    }

    @Test
    void getMediaInsightsMultipleMetrics() throws TimeoutException, QueueException {
        Execution execution = runFlow("instagram_get_media_insights_multiple_test");

        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.getTaskRunList(), hasSize(1));
        assertThat(execution.getTaskRunList().getFirst().getOutputs().get("mediaId"), notNullValue());
        assertThat(execution.getTaskRunList().getFirst().getOutputs().get("totalInsights"), notNullValue());
        assertThat(execution.getTaskRunList().getFirst().getOutputs().get("totalInsights"), is(4));

        @SuppressWarnings("unchecked")
        List<Object> insights = (List<Object>) execution.getTaskRunList().getFirst().getOutputs().get("insights");
        assertThat(insights, hasSize(4));
    }
}