package io.kestra.plugin.meta.facebook;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.*;
import jakarta.annotation.Nullable;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

@Controller("/v24.0")
@Requires(property = "mock.facebook.enabled", value = "true", defaultValue = "true")
@Requires(property = "mock.instagram.enabled", value = "false", defaultValue = "false")
public class MockFacebookApiServer {

    @Post("/{pageId}/feed")
    public HttpResponse<String> createPost(@PathVariable String pageId,
                                           @Header(HttpHeaders.AUTHORIZATION) @Nullable String authorization) {
        return HttpResponse.ok("{\"id\": \"123456789_987654321\"}");
    }

    @Delete("/{postId}")
    public HttpResponse<String> deletePost(@PathVariable String postId,
                                           @Header(HttpHeaders.AUTHORIZATION) @Nullable String authorization) {
        return HttpResponse.ok("{\"success\": true}");
    }

    @Get("/{pageId}/feed")
    public HttpResponse<String> listPosts(@PathVariable String pageId,
                                          @Header(HttpHeaders.AUTHORIZATION) @Nullable String authorization)
        throws IOException {
        return HttpResponse.ok(IOUtils.toString(
            Objects.requireNonNull(
                MockFacebookApiServer.class.getClassLoader().getResourceAsStream(
                    "responses/facebook/list-posts.json")),
            StandardCharsets.UTF_8));
    }

    @Get("/{postId}/insights")
    public HttpResponse<String> getPostInsights(@PathVariable String postId,
                                                @Header(HttpHeaders.AUTHORIZATION) @Nullable String authorization)
        throws IOException {
        return HttpResponse.ok(IOUtils.toString(
            Objects.requireNonNull(MockFacebookApiServer.class.getClassLoader()
                .getResourceAsStream("responses/facebook/post-insights.json")),
            StandardCharsets.UTF_8));
    }
}
