package io.kestra.plugin.meta.instagram.media;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.property.Property;
import io.kestra.core.queues.QueueException;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.plugin.meta.instagram.AbstractInstagramTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@KestraTest
class CreateImageTest extends AbstractInstagramTest {

    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void createImageSuccess() throws TimeoutException, QueueException {
        Execution execution = runFlow("instagram_create_image_test");

        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.getTaskRunList(), hasSize(1));
        assertThat(execution.getTaskRunList().getFirst().getOutputs().get("mediaId"), notNullValue());
        assertThat(execution.getTaskRunList().getFirst().getOutputs().get("mediaId"), is("17954170374002653"));
    }

    @Test
    void createImageWithCaption() throws Exception {
        RunContext runContext = runContextFactory.of();

        CreateImage task = CreateImage.builder()
            .host(Property.ofValue(embeddedServer.getURL().toString()))
            .igId(Property.ofValue("mock-ig-id"))
            .accessToken(Property.ofValue("mock-access-token"))
            .imageUrl(Property.ofValue("https://example.com/test-image.jpg"))
            .caption(Property.ofValue("Test image post with caption from Kestra automation"))
            .build();

        CreateImage.Output output = task.run(runContext);

        assertThat(output, notNullValue());
        assertThat(output.getMediaId(), notNullValue());
    }
}