package io.kestra.plugin.meta.instagram;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@KestraTest
class CreateImagePostTest extends AbstractInstagramTest {

    @Test
    void createImagePost() throws TimeoutException, QueueException {
        Execution execution = runFlow("instagram_create_image_post_test");

        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.getTaskRunList(), hasSize(1));
        assertThat(execution.getTaskRunList().getFirst().getOutputs().get("mediaId"), notNullValue());
        assertThat(execution.getTaskRunList().getFirst().getOutputs().get("mediaId"), is("17954170374002653"));
        assertThat(execution.getTaskRunList().getFirst().getOutputs().get("containerId"), notNullValue());
        assertThat(execution.getTaskRunList().getFirst().getOutputs().get("imageUrl"),
                is("https://example.com/test-image.jpg"));
    }

    @Test
    void createImagePostWithCaption() throws TimeoutException, QueueException {
        Execution execution = runFlow("instagram_create_image_post_with_caption_test");

        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.getTaskRunList(), hasSize(1));
        assertThat(execution.getTaskRunList().getFirst().getOutputs().get("mediaId"), notNullValue());
        assertThat(execution.getTaskRunList().getFirst().getOutputs().get("caption"),
                is("Test image post from Kestra!"));
        assertThat(execution.getTaskRunList().getFirst().getOutputs().get("imageUrl"),
                is("https://example.com/test-image.jpg"));
    }
}