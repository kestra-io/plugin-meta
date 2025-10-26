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
import io.kestra.core.http.client.configurations.HttpConfiguration;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.Builder;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

@SuperBuilder
@NoArgsConstructor
@Getter
@EqualsAndHashCode
@Schema(title = "Schedule a post on a Facebook Page", description = "Schedule content to be published at a future time on a Facebook Page")
@Plugin(examples = {
        @Example(title = "Schedule a post for tomorrow", full = true, code = """
                id: facebook_schedule_post
                namespace: company.team

                tasks:
                  - id: schedule_post
                    type: io.kestra.plugin.meta.facebook.SchedulePost
                    pageId: "{{ secret('FACEBOOK_PAGE_ID') }}"
                    accessToken: "{{ secret('FACEBOOK_ACCESS_TOKEN') }}"
                    message: "This post is scheduled for tomorrow!"
                    scheduledPublishTime: "{{ now() | dateAdd(1, 'DAYS') | date('yyyy-MM-dd HH:mm:ss') }}"
                """),
        @Example(title = "Schedule a post with Unix timestamp", code = """
                - id: schedule_unix_post
                  type: io.kestra.plugin.meta.facebook.SchedulePost
                  pageId: "{{ secret('FACEBOOK_PAGE_ID') }}"
                  accessToken: "{{ secret('FACEBOOK_ACCESS_TOKEN') }}"
                  message: "Scheduled post with timestamp"
                  scheduledPublishTime: "1735689600"
                  link: "https://example.com"
                """)
})
public class SchedulePost extends AbstractFacebookTask {

    @Schema(title = "Post Message", description = "The text content of the post")
    protected Property<String> message;

    @Schema(title = "Link URL", description = "Optional link to include in the post")
    protected Property<String> link;

    @Schema(title = "Scheduled Publish Time", description = "When to publish the post. Accepts Unix timestamp, ISO 8601 string, or any format parsable by PHP's strtotime(). Must be between 10 minutes and 30 days from now.")
    protected Property<String> scheduledPublishTime;

    @Override
    public Output run(RunContext runContext) throws Exception {
        String rPageId = runContext.render(this.pageId).as(String.class).orElseThrow();
        String rToken = runContext.render(this.accessToken).as(String.class).orElseThrow();
        String url = buildApiUrl(runContext, rPageId + "/feed");

        Map<String, Object> postData = new HashMap<>();

        if (this.message != null) {
            runContext.render(this.message).as(String.class).ifPresent(msg -> postData.put("message", msg));
        }

        if (this.link != null) {
            runContext.render(this.link).as(String.class).ifPresent(linkUrl -> postData.put("link", linkUrl));
        }

        String scheduleTime = runContext.render(this.scheduledPublishTime).as(String.class).orElseThrow();
        postData.put("published", false);
        postData.put("scheduled_publish_time", scheduleTime);

        String jsonBody = JacksonMapper.ofJson().writeValueAsString(postData);

        HttpRequest request = HttpRequest.builder()
                .method("POST")
                .uri(URI.create(url))
                .body(HttpRequest.StringRequestBody.builder()
                        .content(jsonBody)
                        .contentType("application/json")
                        .build())
                .addHeader("Authorization", "Bearer " + rToken)
                .addHeader("Content-Type", "application/json")
                .build();

        HttpConfiguration httpConfiguration = HttpConfiguration.builder().build();

        try (HttpClient httpClient = HttpClient.builder()
                .configuration(httpConfiguration)
                .runContext(runContext)
                .build()) {
            HttpResponse<String> response = httpClient.request(request, String.class);

            if (response.getStatus().getCode() != 200) {
                throw new RuntimeException(
                        "Failed to schedule post: " + response.getStatus().getCode() + " - " + response.getBody());
            }

            JsonNode responseJson = JacksonMapper.ofJson().readTree(response.getBody());
            String postId = responseJson.get("id").asText();

            runContext.logger().info("Successfully scheduled Facebook post with ID: {} for time: {}", postId,
                    scheduleTime);

            return Output.builder()
                    .postId(postId)
                    .message(postData.get("message") != null ? postData.get("message").toString() : null)
                    .link(postData.get("link") != null ? postData.get("link").toString() : null)
                    .scheduledPublishTime(scheduleTime)
                    .published(false)
                    .build();
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "The ID of the scheduled post")
        @JsonProperty("postId")
        private final String postId;

        @Schema(title = "The message content of the post")
        @JsonProperty("message")
        private final String message;

        @Schema(title = "The link included in the post")
        @JsonProperty("link")
        private final String link;

        @Schema(title = "The scheduled publish time")
        @JsonProperty("scheduledPublishTime")
        private final String scheduledPublishTime;

        @Schema(title = "Published status (false for scheduled posts)")
        @JsonProperty("published")
        private final Boolean published;
    }
}