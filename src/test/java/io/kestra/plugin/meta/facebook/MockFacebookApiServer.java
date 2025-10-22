package io.kestra.plugin.meta.facebook;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.*;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

@Controller("/v24.0")
public class MockFacebookApiServer {

    @Post("/{pageId}/feed")
    public HttpResponse<String> createPost(@PathVariable String pageId, @QueryValue String access_token)
            throws IOException {
        return HttpResponse.ok(IOUtils.toString(
                Objects.requireNonNull(
                        MockFacebookApiServer.class.getClassLoader().getResourceAsStream("responses/create-post.json")),
                StandardCharsets.UTF_8));
    }

    @Delete("/{postId}")
    public HttpResponse<String> deletePost(@PathVariable String postId, @QueryValue String access_token)
            throws IOException {
        return HttpResponse.ok(IOUtils.toString(
                Objects.requireNonNull(
                        MockFacebookApiServer.class.getClassLoader().getResourceAsStream("responses/delete-post.json")),
                StandardCharsets.UTF_8));
    }

    @Get("/{pageId}/feed")
    public HttpResponse<String> listPosts(@PathVariable String pageId, @QueryValue String access_token)
            throws IOException {
        return HttpResponse.ok(IOUtils.toString(
                Objects.requireNonNull(
                        MockFacebookApiServer.class.getClassLoader().getResourceAsStream("responses/list-posts.json")),
                StandardCharsets.UTF_8));
    }

    @Get("/{postId}/insights")
    public HttpResponse<String> getPostInsights(@PathVariable String postId, @QueryValue String access_token)
            throws IOException {
        return HttpResponse.ok(IOUtils.toString(
                Objects.requireNonNull(MockFacebookApiServer.class.getClassLoader()
                        .getResourceAsStream("responses/post-insights.json")),
                StandardCharsets.UTF_8));
    }
}
