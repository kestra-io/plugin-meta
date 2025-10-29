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

import java.util.List;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
class CreateCarouselTest extends AbstractInstagramTest {

    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void createCarouselSuccess() throws TimeoutException, QueueException {
        Execution execution = runFlow("instagram_create_carousel_test");

        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.getTaskRunList(), hasSize(1));
        assertThat(execution.getTaskRunList().getFirst().getOutputs().get("mediaId"), notNullValue());
    }

    @Test
    void createCarouselWithExcessiveMediaUrls() throws Exception {
        RunContext runContext = runContextFactory.of();

        CreateCarousel task = CreateCarousel.builder()
            .host(Property.ofValue(embeddedServer.getURL().toString()))
            .igId(Property.ofValue("mock-ig-id"))
            .accessToken(Property.ofValue("mock-access-token"))
            .mediaUrls(Property.ofValue(List.of(
                "https://example.com/1.jpg",
                "https://example.com/2.jpg",
                "https://example.com/3.jpg",
                "https://example.com/4.jpg",
                "https://example.com/5.jpg",
                "https://example.com/6.jpg",
                "https://example.com/7.jpg",
                "https://example.com/8.jpg",
                "https://example.com/9.jpg",
                "https://example.com/10.jpg",
                "https://example.com/11.jpg")))
            .caption(Property.ofValue("Test carousel"))
            .build();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> task.run(runContext));
        assertThat(exception.getMessage(),
            containsString("Carousel must contain between 2 and 10 media items"));
    }
}