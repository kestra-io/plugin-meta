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
import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@KestraTest
class DeleteTest extends AbstractFacebookTest {

    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void deletePostsSuccess() throws TimeoutException, QueueException {
        Execution execution = runFlow("facebook_delete_posts_test");

        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.getTaskRunList(), hasSize(1));

        @SuppressWarnings("unchecked")
        List<String> deletedPostIds = (List<String>) execution.getTaskRunList().getFirst().getOutputs()
            .get("deletedPostIds");
        assertThat(deletedPostIds, hasSize(3));
        assertThat(execution.getTaskRunList().getFirst().getOutputs().get("totalDeleted"), is(3));
        assertThat(execution.getTaskRunList().getFirst().getOutputs().get("allSuccess"), is(true));
    }

    @Test
    void deleteSinglePost() throws Exception {
        RunContext runContext = runContextFactory.of();

        Delete task = Delete.builder()
            .apiBaseUrl(Property.ofValue(embeddedServer.getURL().toString()))
            .pageId(Property.ofValue("mock-page-id"))
            .accessToken(Property.ofValue("mock-access-token"))
            .postIds(Property.ofValue(List.of("123456789_987654321")))
            .build();

        Delete.Output output = task.run(runContext);

        assertThat(output, notNullValue());
        assertThat(output.getTotalDeleted(), is(1));
        assertThat(output.getAllSuccess(), is(true));
    }
}
