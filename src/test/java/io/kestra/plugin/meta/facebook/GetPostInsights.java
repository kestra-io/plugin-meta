package io.kestra.plugin.meta.facebook;

import com.fasterxml.jackson.databind.JsonNode;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.HttpResponse;
import io.kestra.core.http.client.HttpClient;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import lombok.Builder;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(title = "Get insights for Facebook posts", description = "Fetch insights and metrics like reach, impressions, and reactions for one or more Facebook posts")
@Plugin(examples = {
        @Example(title = "Get insights for multiple posts", full = true, code = """
                id: facebook_get_post_insights
                namespace: company.team

                tasks:
                  - id: get_insights
                    type: io.kestra.plugin.meta.facebook.GetPostInsights
                    pageId: "{{ secret('FACEBOOK_PAGE_ID') }}"
                    accessToken: "{{ secret('FACEBOOK_ACCESS_TOKEN') }}"
                    postIds:
                      - "123456789_987654321"
                      - "123456789_987654322"
                """),
        @Example(title = "Get specific metrics for posts", code = """
                - id: get_specific_insights
                  type: io.kestra.plugin.meta.facebook.GetPostInsights
                  pageId: "{{ secret('FACEBOOK_PAGE_ID') }}"
                  accessToken: "{{ secret('FACEBOOK_ACCESS_TOKEN') }}"
                  postIds:
                    - "123456789_987654321"
                  metrics: "post_impressions,post_engaged_users,post_reactions_by_type_total"
                  period: "lifetime"
                """)
})
public class GetPostInsights extends AbstractFacebookTask {

    public enum Period {
        DAY,
        WEEK,
        DAYS_28,
        MONTH,
        LIFETIME,
        TOTAL_OVER_RANGE
    }

    @Schema(title = "Post IDs", description = "List of Facebook post IDs to get insights for (format: pageId_postId)")
    @NotNull
    private Property<List<String>> postIds;

    @Schema(title = "Metrics", description = "Comma-separated list of specific metrics to retrieve. If not specified, all available metrics will be retrieved", example = "post_impressions,post_engaged_users,post_reactions_by_type_total")
    private Property<String> metrics;

    @Schema(title = "Period", description = "The aggregation period for insights")
    @Builder.Default
    private Property<Period> period = Property.ofValue(Period.LIFETIME);

    @Schema(title = "Since", description = "Lower bound of the time range to consider (datetime)")
    private Property<String> since;

    @Schema(title = "Until", description = "Upper bound of the time range to consider (datetime)")
    private Property<String> until;

    @Override
    public Output run(RunContext runContext) throws Exception {
        List<String> rPostIds = runContext.render(this.postIds).asList(String.class);
        List<PostInsightsData> results = new ArrayList<>();

        try (HttpClient httpClient = HttpClient.builder()
                .runContext(runContext)
                .build()) {
            for (String postId : rPostIds) {
                try {
                    PostInsightsData postData = getPostInsights(runContext, httpClient, postId);
                    results.add(postData);
                } catch (Exception e) {
                    runContext.logger().error("Failed to retrieve insights for post ID: {}", postId, e);
                    results.add(PostInsightsData.builder()
                            .postId(postId)
                            .totalInsights(0)
                            .insights(new ArrayList<>())
                            .error("Failed: " + e.getMessage())
                            .build());
                }
            }
        }

        int totalInsights = results.stream().mapToInt(PostInsightsData::getTotalInsights).sum();

        runContext.logger().info("Successfully processed {} posts with {} total insights", results.size(),
                totalInsights);

        return Output.builder()
                .posts(results)
                .totalPosts(results.size())
                .totalInsights(totalInsights)
                .build();
    }

    private PostInsightsData getPostInsights(RunContext runContext, HttpClient httpClient, String postId)
            throws Exception {
        String rToken = runContext.render(this.accessToken).as(String.class).orElseThrow();
        StringBuilder urlBuilder = new StringBuilder();
        urlBuilder.append(buildApiUrl(runContext, postId + "/insights"));

        boolean hasParams = false;

        if (this.metrics != null) {
            String rMetrics = runContext.render(this.metrics).as(String.class).orElse(null);
            if (rMetrics != null && !rMetrics.isEmpty()) {
                urlBuilder.append("?metric=").append(URLEncoder.encode(rMetrics, StandardCharsets.UTF_8));
                hasParams = true;
            }
        }

        Period rPeriod = runContext.render(this.period).as(Period.class).orElse(Period.LIFETIME);
        urlBuilder.append(hasParams ? "&" : "?").append("period=").append(rPeriod.name().toLowerCase());

        if (this.since != null) {
            String rSince = runContext.render(this.since).as(String.class).orElse(null);
            if (rSince != null && !rSince.isEmpty()) {
                urlBuilder.append("&since=").append(URLEncoder.encode(rSince, StandardCharsets.UTF_8));
            }
        }

        if (this.until != null) {
            String rUntil = runContext.render(this.until).as(String.class).orElse(null);
            if (rUntil != null && !rUntil.isEmpty()) {
                urlBuilder.append("&until=").append(URLEncoder.encode(rUntil, StandardCharsets.UTF_8));
            }
        }

        String fullUrl = urlBuilder + (urlBuilder.toString().contains("?") ? "&" : "?") + "access_token="
                + URLEncoder.encode(rToken, StandardCharsets.UTF_8);

        HttpRequest request = HttpRequest.builder()
                .uri(URI.create(fullUrl))
                .method("GET")
                .build();

        HttpResponse<String> response = httpClient.request(request, String.class);

        if (response.getStatus().getCode() != 200) {
            throw new RuntimeException(
                    "Failed to get post insights: " + response.getStatus().getCode() + " - " + response.getBody());
        }

        JsonNode responseJson = JacksonMapper.ofJson().readTree(response.getBody());
        return parsePostInsights(postId, responseJson, rPeriod.name().toLowerCase());
    }

    private PostInsightsData parsePostInsights(String postId, JsonNode responseJson, String period) {
        List<Map<String, Object>> insights = new ArrayList<>();
        Map<String, Object> insightsSummary = new HashMap<>();

        if (responseJson.has("data")) {
            JsonNode dataArray = responseJson.get("data");
            for (JsonNode insightNode : dataArray) {
                @SuppressWarnings("unchecked")
                Map<String, Object> insight = JacksonMapper.ofJson().convertValue(insightNode, Map.class);
                insights.add(insight);

                // Create summary by metric name
                if (insight.containsKey("name") && insight.containsKey("values")) {
                    String metricName = insight.get("name").toString();
                    Object values = insight.get("values");
                    insightsSummary.put(metricName, values);
                }
            }
        }

        return PostInsightsData.builder()
                .postId(postId)
                .totalInsights(insights.size())
                .insights(insights)
                .insightsSummary(insightsSummary)
                .period(period)
                .build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        private final List<PostInsightsData> posts;
        private final Integer totalPosts;
        private final Integer totalInsights;
    }

    @Builder
    @Getter
    public static class PostInsightsData {
        private final String postId;
        private final Integer totalInsights;
        private final List<Map<String, Object>> insights;
        private final Map<String, Object> insightsSummary;
        private final String period;
        private final String error;
    }
}