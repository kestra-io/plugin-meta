package io.kestra.plugin.meta.instagram.media;

import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.property.Property;
import io.kestra.core.queues.QueueException;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.plugin.meta.instagram.AbstractInstagramTest;
import io.kestra.plugin.meta.instagram.MockInstagramApiServer;
import io.kestra.plugin.meta.instagram.enums.VideoType;

import jakarta.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
class CreateVideoTest extends AbstractInstagramTest {

    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void createVideoSuccess() throws TimeoutException, QueueException {
        Execution execution = runFlow("instagram_create_video_test");

        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.getTaskRunList(), hasSize(1));
        assertThat(execution.getTaskRunList().getFirst().getOutputs().get("mediaId"), notNullValue());
        assertThat(execution.getTaskRunList().getFirst().getOutputs().get("mediaId"), is("18091026160853193"));
    }

    @Test
    void createReelWithCaption() throws Exception {
        RunContext runContext = runContextFactory.of();

        CreateVideo task = CreateVideo.builder()
            .host(Property.ofValue(embeddedServer.getURL().toString()))
            .igId(Property.ofValue("mock-ig-id"))
            .accessToken(Property.ofValue("mock-access-token"))
            .videoUrl(Property.ofValue("https://example.com/test-reel.mp4"))
            .videoType(Property.ofValue(VideoType.REELS))
            .caption(Property.ofValue("Check out this awesome reel! #automation #kestra"))
            .build();

        CreateVideo.Output output = task.run(runContext);

        assertThat(output, notNullValue());
        assertThat(output.getMediaId(), notNullValue());
        assertThat(output.getMediaId(), is("18091026160853193"));
    }

    /** A container Graph reports as ERROR has to fail now, not after the five minute poll ceiling. */
    @Test
    @Timeout(60)
    void createVideoFailsFastWhenProcessingErrors() {
        RunContext runContext = runContextFactory.of();

        CreateVideo task = CreateVideo.builder()
            .host(Property.ofValue(embeddedServer.getURL().toString()))
            .igId(Property.ofValue("mock-ig-id"))
            .accessToken(Property.ofValue("mock-access-token"))
            .videoUrl(Property.ofValue("https://example.com/%s.mp4".formatted(MockInstagramApiServer.FAILING_VIDEO)))
            .build();

        var thrown = assertThrows(RuntimeException.class, () -> task.run(runContext));

        assertThat(thrown.getMessage(), containsString("Video processing failed for container"));
        assertThat(thrown.getMessage(), not(containsString("Timed out")));
    }
}