package io.kestra.plugin.meta.facebook;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;

import com.facebook.ads.sdk.APIContext;
import com.facebook.ads.sdk.APIRequest;

/**
 * The SDK casts its connection to HttpsURLConnection, so it cannot reach the plain HTTP mock server. This routes
 * the same calls through the JDK client instead, leaving the mock controller as the source of truth.
 */
public final class PlainHttpRequestExecutor implements APIRequest.IRequestExecutor {
    private static APIRequest.IRequestExecutor original;

    /** Swaps the executor in, the SDK holds it statically so tests must put the default back. */
    public static void install() {
        if (original == null) {
            original = APIRequest.getExecutor();
        }

        APIRequest.changeRequestExecutor(new PlainHttpRequestExecutor());
    }

    /** Puts the SDK's own transport back, so a later test in the same JVM is not left on the stub. */
    public static void restore() {
        if (original != null) {
            APIRequest.changeRequestExecutor(original);
        }
    }

    @Override
    public APIRequest.ResponseWrapper execute(String method, String apiUrl, Map<String, Object> params, APIContext context) throws java.io.IOException {
        return switch (method) {
            case "GET" -> sendGet(apiUrl, params, context);
            case "DELETE" -> sendDelete(apiUrl, params, context);
            default -> sendPost(apiUrl, params, context);
        };
    }

    @Override
    public APIRequest.ResponseWrapper sendGet(String apiUrl, Map<String, Object> params, APIContext context) throws java.io.IOException {
        return send(HttpRequest.newBuilder(URI.create("%s?%s".formatted(apiUrl, form(params, context)))).GET());
    }

    @Override
    public APIRequest.ResponseWrapper sendPost(String apiUrl, Map<String, Object> params, APIContext context) throws java.io.IOException {
        return send(
            HttpRequest.newBuilder(URI.create(apiUrl))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form(params, context)))
        );
    }

    @Override
    public APIRequest.ResponseWrapper sendDelete(String apiUrl, Map<String, Object> params, APIContext context) throws java.io.IOException {
        return send(HttpRequest.newBuilder(URI.create("%s?%s".formatted(apiUrl, form(params, context)))).DELETE());
    }

    /** A client per call, closed straight after, so no pooled connection outlives the embedded server. */
    private static APIRequest.ResponseWrapper send(HttpRequest.Builder builder) throws java.io.IOException {
        try (var client = java.net.http.HttpClient.newHttpClient()) {
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());

            return new APIRequest.ResponseWrapper(response.body(), response.headers().map().toString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new java.io.IOException(e);
        }
    }

    private static String form(Map<String, Object> params, APIContext context) {
        var encoded = params.entrySet().stream()
            .map(e -> "%s=%s".formatted(encode(e.getKey()), encode(String.valueOf(e.getValue()))))
            .collect(Collectors.joining("&"));

        return encoded.isEmpty()
            ? "access_token=%s".formatted(encode(context.getAccessToken()))
            : "%s&access_token=%s".formatted(encoded, encode(context.getAccessToken()));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
