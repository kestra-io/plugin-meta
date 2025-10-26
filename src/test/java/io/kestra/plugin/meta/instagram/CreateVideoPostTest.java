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
class CreateVideoPostTest extends AbstractInstagramTest {

    @Test
    void createVideoPost() throws TimeoutException, QueueException {
        Execution execution = runFlow("instagram_create_video_post_test");

        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.getTaskRunList(), hasSize(1));
        assertThat(execution.getTaskRunList().getFirst().getOutputs().get("mediaId"), notNullValue());
        assertThat(execution.getTaskRunList().getFirst().getOutputs().get("mediaId"), is("18091026160853193"));
        assertThat(execution.getTaskRunList().getFirst().getOutputs().get("containerId"), notNullValue());
        assertThat(execution.getTaskRunList().getFirst().getOutputs().get("videoUrl"),
                is("https://example.com/test-video.mp4"));
        assertThat(execution.getTaskRunList().getFirst().getOutputs().get("mediaType"), is("REELS"));
    }

    @Test
    void createVideoPostWithCaption() throws TimeoutException, QueueException {
        Execution execution = runFlow("instagram_create_video_post_with_caption_test");

        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.getTaskRunList(), hasSize(1));
        assertThat(execution.getTaskRunList().getFirst().getOutputs().get("mediaId"), notNullValue());
        assertThat(execution.getTaskRunList().getFirst().getOutputs().get("caption"),
                is("Test reel post from Kestra!"));
        assertThat(execution.getTaskRunList().getFirst().getOutputs().get("mediaType"), is("REELS"));
    }
}