package io.kestra.plugin.meta.instagram;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@KestraTest
class CreateCarouselPostTest extends AbstractInstagramTest {

    @Test
    void createCarouselPost() throws TimeoutException, QueueException {
        Execution execution = runFlow("instagram_create_carousel_post_test");

        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.getTaskRunList(), hasSize(1));
        assertThat(execution.getTaskRunList().getFirst().getOutputs().get("mediaId"), notNullValue());
        assertThat(execution.getTaskRunList().getFirst().getOutputs().get("carouselContainerId"), notNullValue());

        @SuppressWarnings("unchecked")
        List<String> childContainerIds = (List<String>) execution.getTaskRunList().getFirst().getOutputs()
                .get("childContainerIds");
        assertThat(childContainerIds, hasSize(3));

        @SuppressWarnings("unchecked")
        List<String> mediaUrls = (List<String>) execution.getTaskRunList().getFirst().getOutputs().get("mediaUrls");
        assertThat(mediaUrls, hasSize(3));
        assertThat(mediaUrls, contains("https://example.com/image1.jpg", "https://example.com/image2.jpg",
                "https://example.com/video1.mp4"));
    }

    @Test
    void createCarouselPostWithCaption() throws TimeoutException, QueueException {
        Execution execution = runFlow("instagram_create_carousel_post_with_caption_test");

        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.getTaskRunList(), hasSize(1));
        assertThat(execution.getTaskRunList().getFirst().getOutputs().get("mediaId"), notNullValue());
        assertThat(execution.getTaskRunList().getFirst().getOutputs().get("caption"),
                is("Test carousel post from Kestra!"));

        @SuppressWarnings("unchecked")
        List<String> childContainerIds = (List<String>) execution.getTaskRunList().getFirst().getOutputs()
                .get("childContainerIds");
        assertThat(childContainerIds, hasSize(2));
    }
}