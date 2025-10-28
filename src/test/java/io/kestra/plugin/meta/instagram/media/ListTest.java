package io.kestra.plugin.meta.instagram.media;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.property.Property;
import io.kestra.core.queues.QueueException;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.plugin.meta.instagram.AbstractInstagramTest;
import io.kestra.plugin.meta.instagram.enums.MediaField;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@KestraTest
class ListTest extends AbstractInstagramTest {

    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void listMediaSuccess() throws TimeoutException, QueueException {
        Execution execution = runFlow("instagram_list_media_test");

        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.getTaskRunList(), hasSize(1));

        @SuppressWarnings("unchecked")
        List<Object> rows = (List<Object>) execution.getTaskRunList().getFirst().getOutputs().get("rows");
        assertThat(rows, notNullValue());
        assertThat(rows, hasSize(2));
        assertThat(execution.getTaskRunList().getFirst().getOutputs().get("size"), is(2));
    }

    @Test
    void listMediaWithSpecificFields() throws Exception {
        RunContext runContext = runContextFactory.of();

        io.kestra.plugin.meta.instagram.media.List task = io.kestra.plugin.meta.instagram.media.List.builder()
            .host(Property.ofValue(embeddedServer.getURL().toString()))
            .igId(Property.ofValue("mock-ig-id"))
            .accessToken(Property.ofValue("mock-access-token"))
            .fields(Property.ofValue(List.of(
                MediaField.ID,
                MediaField.CAPTION,
                MediaField.MEDIA_TYPE,
                MediaField.TIMESTAMP,
                MediaField.PERMALINK)))
            .limit(Property.ofValue(10))
            .build();

        io.kestra.plugin.meta.instagram.media.List.Output output = task.run(runContext);

        assertThat(output, notNullValue());
        assertThat(output.getRows(), notNullValue());
        assertThat(output.getSize(), greaterThan(0L));
    }
}