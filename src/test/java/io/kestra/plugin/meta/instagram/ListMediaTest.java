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
class ListMediaTest extends AbstractInstagramTest {

    @Test
    void listMedia() throws TimeoutException, QueueException {
        Execution execution = runFlow("instagram_list_media_test");

        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.getTaskRunList(), hasSize(1));
        assertThat(execution.getTaskRunList().getFirst().getOutputs().get("totalCount"), notNullValue());
        assertThat(execution.getTaskRunList().getFirst().getOutputs().get("totalCount"), is(2));

        @SuppressWarnings("unchecked")
        List<Object> mediaItems = (List<Object>) execution.getTaskRunList().getFirst().getOutputs().get("mediaItems");
        assertThat(mediaItems, hasSize(2));
    }

    @Test
    void listMediaWithLimit() throws TimeoutException, QueueException {
        Execution execution = runFlow("instagram_list_media_with_limit_test");

        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.getTaskRunList(), hasSize(1));
        assertThat(execution.getTaskRunList().getFirst().getOutputs().get("totalCount"), notNullValue());
        assertThat(execution.getTaskRunList().getFirst().getOutputs().get("totalCount"), is(1));

        @SuppressWarnings("unchecked")
        List<Object> mediaItems = (List<Object>) execution.getTaskRunList().getFirst().getOutputs().get("mediaItems");
        assertThat(mediaItems, hasSize(1));
    }
}