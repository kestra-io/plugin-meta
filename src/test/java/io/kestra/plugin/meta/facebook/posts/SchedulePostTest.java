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

import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@KestraTest
class SchedulePostTest extends AbstractFacebookTest {

    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void schedulePostsSuccess() throws TimeoutException, QueueException {
        Execution execution = runFlow("facebook_schedule_posts_test");

        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.getTaskRunList(), hasSize(1));
        assertThat(execution.getTaskRunList().getFirst().getOutputs().get("postId"), notNullValue());
        assertThat(execution.getTaskRunList().getFirst().getOutputs().get("postId"), is("123456789_987654321"));
    }

    @Test
    void schedulePostWithFutureDate() throws Exception {
        RunContext runContext = runContextFactory.of();

        long futureTimestamp = System.currentTimeMillis() / 1000 + 86400;

        SchedulePost task = SchedulePost.builder()
                .apiBaseUrl(Property.ofValue(embeddedServer.getURL().toString()))
                .pageId(Property.ofValue("mock-page-id"))
                .accessToken(Property.ofValue("mock-access-token"))
                .message(Property.ofValue("This post is scheduled for the future!"))
                .scheduledPublishTime(Property.ofValue(String.valueOf(futureTimestamp)))
                .build();

        SchedulePost.Output output = task.run(runContext);

        assertThat(output, notNullValue());
        assertThat(output.getPostId(), notNullValue());
    }
}