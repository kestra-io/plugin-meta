package io.kestra.plugin.meta.instagram;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.HttpResponse;
import io.kestra.core.http.client.HttpClient;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.plugin.meta.instagram.enums.InsightMetric;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@SuperBuilder
@NoArgsConstructor
@Getter
@Schema(
    title = "Get Instagram media insights",
    description = "Fetch insights like reach, saves, likes, etc. for specific Instagram media"
)
@Plugin(
    examples = {
        @Example(
            title = "Get media insights",
            full = true,
            code = """
                id: instagram_get_media_insights
                namespace: company.team

                tasks:
                  - id: get_insights
                    type: io.kestra.plugin.meta.instagram.GetMediaInsights
                    igId: "{{ secret('INSTAGRAM_ACCOUNT_ID') }}"
                    accessToken: "{{ secret('INSTAGRAM_ACCESS_TOKEN') }}"
                    mediaId: "17954170374002653"
                    metrics:
                      - LIKES
                      - COMMENTS
                      - SAVES
                      - REACH
                """
        )
    }
)
public class GetMediaInsights extends AbstractInstagramTask {

    @Schema(title = "Media ID", description = "The ID of the Instagram media to get insights for")
    @NotNull
    protected Property<String> mediaId;

    @Schema(title = "Metrics", description = "List of insight metrics to retrieve")
    @Builder.Default
    protected Property<List<InsightMetric>> metrics = Property.ofValue(
        List.of(InsightMetric.LIKES, InsightMetric.COMMENTS, InsightMetric.SAVES, InsightMetric.REACH));

    @Override
    public Output run(RunContext runContext) throws Exception {
        String rMediaId = runContext.render(this.mediaId).as(String.class).orElseThrow();
        String rToken = runContext.render(this.accessToken).as(String.class).orElseThrow();
        List<InsightMetric> rMetrics = runContext.render(this.metrics).asList(InsightMetric.class);

        String metricsParam = rMetrics.stream()
            .map(metric -> metric.name().toLowerCase())
            .collect(Collectors.joining(","));

        String url = buildApiUrl(runContext, rMediaId + "/insights");

        HttpRequest request = HttpRequest.builder()
            .method("GET")
            .uri(URI.create(url + "?metric=" + metricsParam))
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer " + rToken)
            .build();

        try (HttpClient httpClient = HttpClient.builder()
            .runContext(runContext)
            .build()) {
            HttpResponse<String> response = httpClient.request(request, String.class);

            if (response.getStatus().getCode() != 200) {
                throw new RuntimeException(
                    "Failed to get media insights: " + response.getStatus().getCode() + " - " + response.getBody());
            }

            JsonNode responseJson = JacksonMapper.ofJson().readTree(response.getBody());
            JsonNode dataNode = responseJson.get("data");

            List<Insight> insights = new ArrayList<>();
            if (dataNode != null && dataNode.isArray()) {
                for (JsonNode insightNode : dataNode) {
                    String name = insightNode.has("name") ? insightNode.get("name").asText() : null;
                    String period = insightNode.has("period") ? insightNode.get("period").asText() : null;
                    String title = insightNode.has("title") ? insightNode.get("title").asText() : null;
                    String description = insightNode.has("description") ? insightNode.get("description").asText()
                        : null;

                    Integer value = null;
                    JsonNode valuesNode = insightNode.get("values");
                    if (valuesNode != null && valuesNode.isArray() && !valuesNode.isEmpty()) {
                        JsonNode firstValue = valuesNode.get(0);
                        if (firstValue.has("value")) {
                            value = firstValue.get("value").asInt();
                        }
                    }

                    Insight insight = Insight.builder()
                        .name(name)
                        .period(period)
                        .title(title)
                        .description(description)
                        .value(value)
                        .build();
                    insights.add(insight);
                }
            }

            runContext.logger().info("Successfully retrieved insights for media ID: {}", rMediaId);

            return Output.builder()
                .mediaId(rMediaId)
                .insights(insights)
                .totalInsights(insights.size())
                .build();
        }
    }

    @Builder
    @Getter
    public static class Insight {
        @Schema(title = "Metric name")
        @JsonProperty("name")
        private final String name;

        @Schema(title = "Time period")
        @JsonProperty("period")
        private final String period;

        @Schema(title = "Metric title")
        @JsonProperty("title")
        private final String title;

        @Schema(title = "Metric description")
        @JsonProperty("description")
        private final String description;

        @Schema(title = "Metric value")
        @JsonProperty("value")
        private final Integer value;
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "Media ID")
        @JsonProperty("mediaId")
        private final String mediaId;

        @Schema(title = "List of insights")
        @JsonProperty("insights")
        private final List<Insight> insights;

        @Schema(title = "Total number of insights")
        @JsonProperty("totalInsights")
        private final Integer totalInsights;
    }
}
