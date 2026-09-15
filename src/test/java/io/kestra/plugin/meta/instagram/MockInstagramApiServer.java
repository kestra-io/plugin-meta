package io.kestra.plugin.meta.instagram;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.io.IOUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;

@Controller("/v24.0")
@Requires(property = "mock.instagram.enabled", value = "true", defaultValue = "true")
@Requires(property = "mock.facebook.enabled", value = "false", defaultValue = "false")
public class MockInstagramApiServer {

    /** Any video URL carrying this marker gets a container Graph reports as ERROR. */
    public static final String FAILING_VIDEO = "processing-error";

    /** Any video URL carrying this marker gets a container whose first status poll never answers in time. */
    public static final String STALLING_VIDEO = "first-poll-stalls";

    private static final String FAILING_CONTAINER_ID = "17910412629238320";

    private static final String STALLING_CONTAINER_ID = "17910412629238321";

    /** Only the first poll of the stalling container hangs, the rest answer, so a wedged poller never recovers. */
    private static final AtomicBoolean stalledOnce = new AtomicBoolean();

    private static final Map<String, String> containerMediaTypes = new ConcurrentHashMap<>();
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // Create media container (POST /{ig_id}/media)
    @Consumes({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_FORM_URLENCODED })
    @Post("/{igId}/media")
    public HttpResponse<String> createMediaContainer(
        @PathVariable String igId,
        @Header(HttpHeaders.AUTHORIZATION) @Nullable String authorization,
        @Body String body) {
        // Parse the body to check media_type and store it
        // a distinct id rather than a flag, so a failing container cannot bleed into another test
        String videoUrl = String.valueOf(field(body, "video_url"));
        String containerId = "17910412629238319"; // Container ID
        if (videoUrl.contains(FAILING_VIDEO)) {
            containerId = FAILING_CONTAINER_ID;
        } else if (videoUrl.contains(STALLING_VIDEO)) {
            containerId = STALLING_CONTAINER_ID;
            stalledOnce.set(false);
        }
        // the container id is constant, so clear on absence or a previous video run leaks into the next image run
        String mediaType = field(body, "media_type");
        if (mediaType != null) {
            containerMediaTypes.put(containerId, mediaType);
        } else {
            containerMediaTypes.remove(containerId);
        }

        return HttpResponse.ok("{\"id\": \"" + containerId + "\"}");
    }

    // Publish media (POST /{ig_id}/media_publish)
    @Consumes({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_FORM_URLENCODED })
    @Post("/{igId}/media_publish")
    public HttpResponse<String> publishMedia(
        @PathVariable String igId,
        @Header(HttpHeaders.AUTHORIZATION) @Nullable String authorization,
        @Body String body) {
        String mediaId = "17954170374002653";

        String containerId = field(body, "creation_id");
        if (containerId != null) {
            String mediaType = containerMediaTypes.get(containerId);

            if (mediaType != null && (mediaType.equals("REELS") || mediaType.equals("VIDEO"))) {
                mediaId = "18091026160853193"; // Video media ID
            }
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

        return HttpResponse.ok(
            IOUtils.toString(
                Objects.requireNonNull(
                    MockInstagramApiServer.class.getClassLoader()
                        .getResourceAsStream(
                            "responses/instagram/" + responseFile
                        )
                ),
                StandardCharsets.UTF_8
            )
        );
    }

    @Get("/{mediaId}/insights")
    public HttpResponse<String> getMediaInsights(
        @PathVariable String mediaId,
        @Header(HttpHeaders.AUTHORIZATION) @Nullable String authorization,
        @Nullable @QueryValue String metric) throws IOException {
        String responseFile = (metric != null && metric.contains(","))
            ? "instagram-insights-multiple.json"
            : "instagram-insights.json";

        return HttpResponse.ok(
            IOUtils.toString(
                Objects.requireNonNull(
                    MockInstagramApiServer.class.getClassLoader()
                        .getResourceAsStream(
                            "responses/instagram/" + responseFile
                        )
                ),
                StandardCharsets.UTF_8
            )
        );
    }

    // Get container status (GET /{container_id}?fields=status_code)
    @Get("/{containerId}")
    public HttpResponse<String> getContainerStatus(
        @PathVariable String containerId,
        @Header(HttpHeaders.AUTHORIZATION) @Nullable String authorization,
        @Nullable @QueryValue String fields) throws IOException {
        // never answers, which is the real shape of a stalled Graph: the SDK sets no read timeout so the poll that
        // hit it is gone for good, and only a fresh thread can carry the next one. Interruptible so server shutdown
        // does not wait on it.
        if (STALLING_CONTAINER_ID.equals(containerId) && stalledOnce.compareAndSet(false, true)) {
            try {
                Thread.sleep(Duration.ofMinutes(10));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // FINISHED lets the other tests publish immediately, the one failing container reports Graph's terminal state
        String status = FAILING_CONTAINER_ID.equals(containerId) ? "ERROR" : "FINISHED";

        return HttpResponse.ok("{\"status_code\":\"" + status + "\",\"id\":\"" + containerId + "\"}");
    }

    /** The SDK posts form encoded, the pre-SDK tasks post JSON, so read a field from either shape. */
    private static String field(String body, String name) {
        if (body == null) {
            return null;
        }

        try {
            JsonNode bodyJson = objectMapper.readTree(body);
            if (bodyJson.has(name)) {
                return bodyJson.get(name).asText();
            }
        } catch (Exception ignored) {
        }

        for (String pair : body.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && name.equals(URLDecoder.decode(kv[0], StandardCharsets.UTF_8))) {
                return URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
            }
        }

        return null;
    }
}
