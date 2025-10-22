package io.kestra.plugin.meta.facebook;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@KestraTest
class ListPostsTest extends AbstractFacebookTest {

    @Test
    void listPosts() throws TimeoutException, QueueException {
        Execution execution = runFlow("facebook_list_posts_test");

        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.getTaskRunList(), hasSize(1));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> posts = (List<Map<String, Object>>) execution.getTaskRunList().getFirst().getOutputs()
                .get("posts");
        assertThat(posts, notNullValue());
        assertThat(posts, hasSize(2));
        assertThat(execution.getTaskRunList().getFirst().getOutputs().get("count"), is(2));
    }

    @Test
    void listPostsWithFields() throws TimeoutException, QueueException {
        Execution execution = runFlow("facebook_list_posts_with_fields_test");

        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.getTaskRunList(), hasSize(1));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> posts = (List<Map<String, Object>>) execution.getTaskRunList().getFirst().getOutputs()
                .get("posts");
        assertThat(posts, notNullValue());
        assertThat(posts.getFirst().containsKey("id"), is(true));
        assertThat(posts.getFirst().containsKey("message"), is(true));
    }
}
