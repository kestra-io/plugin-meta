package io.kestra.plugin.meta.facebook;

import com.fasterxml.jackson.annotation.JsonProperty;
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
import io.kestra.plugin.meta.facebook.enums.*;

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
import java.time.LocalDate;
import java.util.*;

@SuperBuilder
@NoArgsConstructor
@Getter
@ToString
@EqualsAndHashCode
@Schema(
    title = "Get insights for Facebook posts",
    description = "Fetch insights and metrics like reach, impressions, and reactions for one or more Facebook posts"
)
@Plugin(
    examples = {
        @Example(
            title = "Get default reaction insights for posts",
            full = true,
            code = """
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
                """
        ),
        @Example(
            title = "Add custom metrics to default reactions",
            code = """
                - id: get_insights_with_custom_metrics
                  type: io.kestra.plugin.meta.facebook.GetPostInsights
                  pageId: "{{ secret('FACEBOOK_PAGE_ID') }}"
                  accessToken: "{{ secret('FACEBOOK_ACCESS_TOKEN') }}"
                  postIds:
                    - "123456789_987654321"
                  metrics:
                    - "post_reactions_like_total"
                    - "post_reactions_love_total"
                    - "post_reactions_wow_total"
                    - "post_reactions_haha_total"
                    - "post_reactions_sorry_total"
                    - "post_reactions_anger_total"
                  period: "lifetime"
                """
        ),
        @Example(
            title = "Get insights with date preset",
            code = """
                - id: get_insights_last_7_days
                  type: io.kestra.plugin.meta.facebook.GetPostInsights
                  pageId: "{{ secret('FACEBOOK_PAGE_ID') }}"
                  accessToken: "{{ secret('FACEBOOK_ACCESS_TOKEN') }}"
                  postIds:
                    - "123456789_987654321"
                  datePreset: "last_7d"
                  period: "day"
                """
        ),
        @Example(
            title = "Get insights with custom date range",
            code = """
                - id: get_insights_custom_range
                  type: io.kestra.plugin.meta.facebook.GetPostInsights
                  pageId: "{{ secret('FACEBOOK_PAGE_ID') }}"
                  accessToken: "{{ secret('FACEBOOK_ACCESS_TOKEN') }}"
                  postIds:
                    - "123456789_987654321"
                  since: "2025-10-01"
                  until: "2025-10-15"
                  period: "day"
                """
        )
    }
)
public class GetPostInsights extends AbstractFacebookTask {

    @Schema(title = "Post IDs", description = "List of Facebook post IDs to get insights for (format: pageId_postId)")
    @NotNull
    private Property<List<String>> postIds;

    @Schema(title = "Date Preset", description = "Preset a date range, like last_week, yesterday. If since or until presents, it does not work.")
    @Builder.Default
    private Property<DatePreset> datePreset = Property.ofValue(DatePreset.TODAY);

    @Schema(title = "Metrics", description = "List of specific metrics to retrieve. Default includes reaction metrics (like, love, wow, haha, sorry, anger, by_type_total). You can add more metrics like post_impressions, post_engaged_users, etc.", example = "[\"post_impressions\", \"post_engaged_users\"]")
    @Builder.Default
    private Property<List<String>> metrics = Property.ofValue(Arrays.asList(
            "post_reactions_like_total",
            "post_reactions_love_total",
            "post_reactions_wow_total",
            "post_reactions_haha_total",
            "post_reactions_sorry_total",
            "post_reactions_anger_total"));

    @Schema(title = "Period", description = "The aggregation period for insights")
    @Builder.Default
    private Property<Period> period = Property.ofValue(Period.LIFETIME);

    @Schema(title = "Since", description = "Lower bound of the time range to consider (datetime). If provided, date_preset does not work.")
    @Builder.Default
    private Property<String> since = Property.ofValue(LocalDate.now().toString());

    @Schema(title = "Until", description = "Upper bound of the time range to consider (datetime). If provided, date_preset does not work.")
    @Builder.Default
    private Property<String> until = Property.ofValue(LocalDate.now().toString());

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
        List<String> rMetrics = runContext.render(this.metrics).asList(String.class);
        Period rPeriod = runContext.render(this.period).as(Period.class).orElse(Period.LIFETIME);
        String rSince = runContext.render(this.since).as(String.class).orElse("");
        String rUntil = runContext.render(this.until).as(String.class).orElse("");
        DatePreset rDatePreset = runContext.render(this.datePreset).as(DatePreset.class).orElse(null);

        StringBuilder urlBuilder = new StringBuilder();
        urlBuilder.append(buildApiUrl(runContext, postId + "/insights"));
        urlBuilder.append("?period=").append(rPeriod.name().toLowerCase());

            String metricsStr = String.join(",", rMetrics);
            urlBuilder.append("&metric=").append(URLEncoder.encode(metricsStr, StandardCharsets.UTF_8));

        if (rDatePreset != null && rSince.isEmpty() && rUntil.isEmpty()) {
            urlBuilder.append("&date_preset=").append(rDatePreset.name().toLowerCase());
        }

            urlBuilder.append("&since=").append(URLEncoder.encode(rSince, StandardCharsets.UTF_8));

            urlBuilder.append("&until=").append(URLEncoder.encode(rUntil, StandardCharsets.UTF_8));

        urlBuilder.append("&access_token=").append(URLEncoder.encode(rToken, StandardCharsets.UTF_8));
        String fullUrl = urlBuilder.toString();

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
        @Schema(title = "List of post insights data")
        @JsonProperty("posts")
        private final List<PostInsightsData> posts;

        @Schema(title = "Total number of posts processed")
        @JsonProperty("totalPosts")
        private final Integer totalPosts;

        @Schema(title = "Total number of insights retrieved")
        @JsonProperty("totalInsights")
        private final Integer totalInsights;
    }

    @Builder
    @Getter
    public static class PostInsightsData {
        @Schema(title = "The post ID")
        @JsonProperty("postId")
        private final String postId;

        @Schema(title = "Total number of insights for this post")
        @JsonProperty("totalInsights")
        private final Integer totalInsights;

        @Schema(title = "Detailed insights data")
        @JsonProperty("insights")
        private final List<Map<String, Object>> insights;

        @Schema(title = "Summary of insights by metric name")
        @JsonProperty("insightsSummary")
        private final Map<String, Object> insightsSummary;

        @Schema(title = "The period used for insights")
        @JsonProperty("period")
        private final String period;

        @Schema(title = "Error message if insights retrieval failed")
        @JsonProperty("error")
        private final String error;
    }
}