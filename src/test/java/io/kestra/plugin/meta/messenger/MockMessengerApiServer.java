package io.kestra.plugin.meta.messenger;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;

/** Stands in for the Graph Send API, so the SDK path can be driven without the url override. */
@Controller("/v23.0")
public class MockMessengerApiServer {
    // concurrent flows write here, a plain ArrayList races with the clear() in @BeforeEach
    public static final List<String> bodies = new CopyOnWriteArrayList<>();

    @Consumes({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_FORM_URLENCODED })
    @Post("/{pageId}/messages")
    public HttpResponse<String> sendMessage(@PathVariable String pageId, @Body String body) {
        bodies.add(body);

        return HttpResponse.ok("{\"message_id\": \"mid.test\", \"recipient_id\": \"24745216345137108\"}");
    }
}
