package io.kestra.plugin.meta.facebook;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@KestraTest
class SchedulePostTest extends AbstractFacebookTest {

    @Test
    void schedulePost() throws TimeoutException, QueueException {
        Execution execution = runFlow("facebook_schedule_post_test");

        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.getTaskRunList(), hasSize(1));
        assertThat(execution.getTaskRunList().getFirst().getOutputs().get("postId"), notNullValue());
    }

    @Test
    void schedulePostWithLink() throws TimeoutException, QueueException {
        Execution execution = runFlow("facebook_schedule_post_with_link_test");

        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.getTaskRunList(), hasSize(1));
        assertThat(execution.getTaskRunList().getFirst().getOutputs().get("postId"), notNullValue());
        assertThat(execution.getTaskRunList().getFirst().getOutputs().get("link"), notNullValue());
    }
}