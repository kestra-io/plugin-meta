package io.kestra.plugin.meta.facebook.posts;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueException;
import io.kestra.plugin.meta.facebook.AbstractFacebookTest;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@KestraTest
class CreateTest extends AbstractFacebookTest {

    @Test
    void createSimplePost() throws TimeoutException, QueueException {
        Execution execution = runFlow("facebook_create_post_test");

        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.getTaskRunList(), hasSize(1));
        assertThat(execution.getTaskRunList().getFirst().getOutputs().get("postId"), notNullValue());
        assertThat(execution.getTaskRunList().getFirst().getOutputs().get("postId"), is("123456789_987654321"));
    }

    @Test
    void createPostWithLink() throws TimeoutException, QueueException {
        Execution execution = runFlow("facebook_create_post_with_link_test");

        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.getTaskRunList(), hasSize(1));
        assertThat(execution.getTaskRunList().getFirst().getOutputs().get("postId"), notNullValue());
        assertThat(execution.getTaskRunList().getFirst().getOutputs().get("link"), is("https://kestra.io"));
    }
}
