package io.kestra.plugin.meta;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;

@Controller("/webhook-unit-test")
public class FakeWebhookController {
    /** Every post lands here, a single last-writer-wins field loses one when concurrent flows both notify. */
    public static final List<String> bodies = new CopyOnWriteArrayList<>();

    public static Map<String, String> headers = new HashMap<>();

    /** Null until something arrives, which is the shape the wait helpers poll on. */
    public static String last() {
        return bodies.isEmpty() ? null : bodies.getLast();
    }

    @Post
    @Consumes({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_FORM_URLENCODED })
    public HttpResponse<String> post(@Body String data) {
        bodies.add(data);
        return HttpResponse.ok("ok");
    }

    @Post("/with-headers")
    @Consumes({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_FORM_URLENCODED })
    public HttpResponse<String> postWithHeaders(HttpRequest<?> request, @Body String data) {

        bodies.add(data);
        request.getHeaders().forEach((name, values) ->
        {
            if (!values.isEmpty()) {
                headers.put(name, values.get(0));
            }
        });

        return HttpResponse.ok("ok");
    }
}
