package io.kestra.plugin.meta.instagram;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.*;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Controller("/v24.0")
@Requires(property = "mock.instagram.enabled", value = "true", defaultValue = "true")
@Requires(property = "mock.facebook.enabled", value = "false", defaultValue = "false")
public class MockInstagramApiServer {

    private static final Map<String, String> containerMediaTypes = new ConcurrentHashMap<>();
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // Create media container (POST /{ig_id}/media)
    @Post("/{igId}/media")
    public HttpResponse<String> createMediaContainer(
            @PathVariable String igId,
            @Header(HttpHeaders.AUTHORIZATION) @Nullable String authorization,
            @Body String body) {
        // Parse the body to check media_type and store it
        String containerId = "17910412629238319"; // Container ID
        try {
            JsonNode bodyJson = objectMapper.readTree(body);
            if (bodyJson.has("media_type")) {
                String mediaType = bodyJson.get("media_type").asText();
                containerMediaTypes.put(containerId, mediaType);
            }
        } catch (Exception e) {
            // Ignore parsing errors, use default behavior
        }

        return HttpResponse.ok("{\"id\": \"" + containerId + "\"}");
    }

    // Publish media (POST /{ig_id}/media_publish)
    @Post("/{igId}/media_publish")
    public HttpResponse<String> publishMedia(
            @PathVariable String igId,
            @Header(HttpHeaders.AUTHORIZATION) @Nullable String authorization,
            @Body String body) {
        // Check if this was a video container by looking up the container ID
        String mediaId = "17954170374002653"; // Default media ID

        try {
            JsonNode bodyJson = objectMapper.readTree(body);
            if (bodyJson.has("creation_id")) {
                String containerId = bodyJson.get("creation_id").asText();
                String mediaType = containerMediaTypes.get(containerId);

                if (mediaType != null && (mediaType.equals("REELS") || mediaType.equals("VIDEO"))) {
                    mediaId = "18091026160853193"; // Video media ID
                }
            }
        } catch (Exception e) {
            // Ignore parsing errors, use default media ID
        }

        return HttpResponse.ok("{\"id\": \"" + mediaId + "\"}");
    }

    @Get("/{igId}/media")
    public HttpResponse<String> listMedia(
            @PathVariable String igId,
            @Header(HttpHeaders.AUTHORIZATION) @Nullable String authorization,
            @Nullable @QueryValue Integer limit) throws IOException {
        String responseFile = (limit != null && limit == 1)
                ? "instagram-list-media-limited.json"
                : "instagram-list-media.json";

        return HttpResponse.ok(IOUtils.toString(
                Objects.requireNonNull(
                        MockInstagramApiServer.class.getClassLoader()
                                .getResourceAsStream(
                                        "responses/instagram/" + responseFile)),
                StandardCharsets.UTF_8));
    }

    @Get("/{mediaId}/insights")
    public HttpResponse<String> getMediaInsights(
            @PathVariable String mediaId,
            @Header(HttpHeaders.AUTHORIZATION) @Nullable String authorization,
            @Nullable @QueryValue String metric) throws IOException {
        String responseFile = (metric != null && metric.contains(","))
                ? "instagram-insights-multiple.json"
                : "instagram-insights.json";

        return HttpResponse.ok(IOUtils.toString(
                Objects.requireNonNull(
                        MockInstagramApiServer.class.getClassLoader()
                                .getResourceAsStream(
                                        "responses/instagram/" + responseFile)),
                StandardCharsets.UTF_8));
    }

    // Get container status (GET /{container_id}?fields=status_code)
    @Get("/{containerId}")
    public HttpResponse<String> getContainerStatus(
            @PathVariable String containerId,
            @Header(HttpHeaders.AUTHORIZATION) @Nullable String authorization,
            @Nullable @QueryValue String fields) throws IOException {
        // Always return FINISHED status to allow immediate publishing in tests
        return HttpResponse.ok("{\"status_code\":\"FINISHED\",\"id\":\"" + containerId + "\"}");
    }
}
