package io.kestra.plugin.meta.facebook.posts;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.property.Property;
import io.kestra.core.queues.QueueException;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.plugin.meta.facebook.AbstractFacebookTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@KestraTest
class ListTest extends AbstractFacebookTest {

    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void listPostsSuccess() throws TimeoutException, QueueException {
        Execution execution = runFlow("facebook_list_posts_test");

        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.getTaskRunList(), hasSize(1));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) execution.getTaskRunList().getFirst().getOutputs()
                .get("rows");
        assertThat(rows, notNullValue());
        assertThat(rows, hasSize(2));
        assertThat(execution.getTaskRunList().getFirst().getOutputs().get("size"), is(2));
    }

    @Test
    void listPostsWithLimit() throws Exception {
        RunContext runContext = runContextFactory.of();

        io.kestra.plugin.meta.facebook.posts.List task = io.kestra.plugin.meta.facebook.posts.List.builder()
                .apiBaseUrl(Property.ofValue(embeddedServer.getURL().toString()))
                .pageId(Property.ofValue("mock-page-id"))
                .accessToken(Property.ofValue("mock-access-token"))
                .limit(Property.ofValue(5))
                .build();

        io.kestra.plugin.meta.facebook.posts.List.Output output = task.run(runContext);

        assertThat(output, notNullValue());
        assertThat(output.getRows(), notNullValue());
        assertThat(output.getSize(), greaterThan(0L));
    }
}
