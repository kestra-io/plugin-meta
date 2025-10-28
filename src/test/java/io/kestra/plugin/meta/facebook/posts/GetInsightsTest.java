package io.kestra.plugin.meta.facebook.posts;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.property.Property;
import io.kestra.core.queues.QueueException;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.plugin.meta.facebook.AbstractFacebookTest;
import io.kestra.plugin.meta.facebook.enums.DatePreset;
import io.kestra.plugin.meta.facebook.enums.PostMetric;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@KestraTest
class GetInsightsTest extends AbstractFacebookTest {

    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void getInsightsSuccess() throws TimeoutException, QueueException {
        Execution execution = runFlow("facebook_get_insights_test");

        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.getTaskRunList(), hasSize(1));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> posts = (List<Map<String, Object>>) execution.getTaskRunList().getFirst().getOutputs()
                .get("posts");

        assertThat(posts, notNullValue());
        assertThat(posts, hasSize(greaterThan(0)));
        assertThat(execution.getTaskRunList().getFirst().getOutputs().get("totalPosts"), is(1));
        assertThat(execution.getTaskRunList().getFirst().getOutputs().get("totalInsights"), is(2));
    }

    @Test
    void getInsightsWithSpecificMetrics() throws Exception {
        RunContext runContext = runContextFactory.of();

        GetInsights task = GetInsights.builder()
                .apiBaseUrl(Property.ofValue(embeddedServer.getURL().toString()))
                .pageId(Property.ofValue("mock-page-id"))
                .accessToken(Property.ofValue("mock-access-token"))
                .postIds(Property.ofValue(List.of("123456789_987654321")))
                .metrics(Property.ofValue(List.of(
                        PostMetric.POST_IMPRESSIONS,
                        PostMetric.POST_ENGAGED_USERS,
                        PostMetric.POST_CLICKS)))
                .datePreset(Property.ofValue(DatePreset.LAST_7D))
                .build();

        GetInsights.Output output = task.run(runContext);

        assertThat(output, notNullValue());
        assertThat(output.getPosts(), notNullValue());
        assertThat(output.getPosts().size(), greaterThan(0));
        assertThat(output.getTotalInsights(), greaterThan(0));
    }
}
