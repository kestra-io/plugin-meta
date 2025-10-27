package io.kestra.plugin.meta.facebook.posts;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueException;
import io.kestra.plugin.meta.facebook.AbstractFacebookTest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@KestraTest
class DeleteTest extends AbstractFacebookTest {

    @Test
    void deleteSinglePost() throws TimeoutException, QueueException {
        Execution execution = runFlow("facebook_delete_post_test");

        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.getTaskRunList(), hasSize(1));

        @SuppressWarnings("unchecked")
        List<String> deletedPostIds = (List<String>) execution.getTaskRunList().getFirst().getOutputs()
                .get("deletedPostIds");
        assertThat(deletedPostIds, hasSize(1));
        assertThat(deletedPostIds.getFirst(), is("123456789_987654321"));
        assertThat(execution.getTaskRunList().getFirst().getOutputs().get("allSuccess"), is(true));
    }

    @Test
    void deleteMultiplePosts() throws TimeoutException, QueueException {
        Execution execution = runFlow("facebook_delete_multiple_posts_test");

        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.getTaskRunList(), hasSize(1));

        @SuppressWarnings("unchecked")
        List<String> deletedPostIds = (List<String>) execution.getTaskRunList().getFirst().getOutputs()
                .get("deletedPostIds");
        assertThat(deletedPostIds, hasSize(3));
        assertThat(execution.getTaskRunList().getFirst().getOutputs().get("totalDeleted"), is(3));
    }
}
