package io.kestra.plugin.meta.facebook.posts;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.property.Property;
import io.kestra.core.queues.QueueException;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.plugin.meta.facebook.AbstractFacebookTest;
import io.kestra.plugin.meta.facebook.MockFacebookApiServer;
import io.kestra.plugin.meta.facebook.enums.DatePreset;
import io.kestra.plugin.meta.facebook.enums.Period;
import io.kestra.plugin.meta.facebook.enums.PostMetric;

import jakarta.inject.Inject;

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
            .metrics(
                Property.ofValue(
                    List.of(
                        PostMetric.POST_IMPRESSIONS,
                        PostMetric.POST_ENGAGED_USERS,
                        PostMetric.POST_CLICKS
                    )
                )
            )
            .datePreset(Property.ofValue(DatePreset.LAST_7D))
            .build();

        GetInsights.Output output = task.run(runContext);

        assertThat(output, notNullValue());
        assertThat(output.getPosts(), notNullValue());
        assertThat(output.getPosts().size(), greaterThan(0));
        assertThat(output.getTotalInsights(), greaterThan(0));
    }

    private GetInsights.GetInsightsBuilder<?, ?> baseTask() {
        return GetInsights.builder()
            .apiBaseUrl(Property.ofValue(embeddedServer.getURL().toString()))
            .pageId(Property.ofValue("mock-page-id"))
            .accessToken(Property.ofValue("mock-access-token"))
            .postIds(Property.ofValue(List.of("123456789_987654321")));
    }

    @Test
    void datePresetIsSentWhenNoBoundsAreSet() throws Exception {
        GetInsights.Output output = baseTask()
            .datePreset(Property.ofValue(DatePreset.LAST_7D))
            .build()
            .run(runContextFactory.of());

        assertThat(output.getPosts().getFirst().getError(), nullValue());
        Map<String, String> query = MockFacebookApiServer.lastInsightsQuery;
        assertThat(query, hasEntry("date_preset", "last_7d"));
        assertThat(query, not(hasKey("since")));
        assertThat(query, not(hasKey("until")));
    }

    @Test
    void boundsOverrideDatePreset() throws Exception {
        GetInsights.Output output = baseTask()
            .datePreset(Property.ofValue(DatePreset.LAST_7D))
            .since(Property.ofValue("2025-10-01"))
            .until(Property.ofValue("2025-10-15"))
            .build()
            .run(runContextFactory.of());

        assertThat(output.getPosts().getFirst().getError(), nullValue());
        Map<String, String> query = MockFacebookApiServer.lastInsightsQuery;
        assertThat(query, hasEntry("since", "2025-10-01"));
        assertThat(query, hasEntry("until", "2025-10-15"));
        assertThat(query, not(hasKey("date_preset")));
    }

    @Test
    void examplesUseValidEnumValues() {
        Plugin plugin = GetInsights.class.getAnnotation(Plugin.class);
        Pattern property = Pattern.compile("^\\s*(period|datePreset):\\s*\"?([^\"\\s]+)\"?\\s*$", Pattern.MULTILINE);

        int checked = 0;
        for (Example example : plugin.examples()) {
            Matcher matcher = property.matcher(String.join("\n", example.code()));
            while (matcher.find()) {
                String value = matcher.group(2);
                if (matcher.group(1).equals("period")) {
                    assertThat("period: " + value, Period.valueOf(value), notNullValue());
                } else {
                    assertThat("datePreset: " + value, DatePreset.valueOf(value), notNullValue());
                }
                checked++;
            }
        }
        assertThat(checked, greaterThan(0));
    }
}
