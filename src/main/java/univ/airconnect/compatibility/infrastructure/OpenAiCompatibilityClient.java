package univ.airconnect.compatibility.infrastructure;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import univ.airconnect.compatibility.config.OpenAiCompatibilityProperties;
import univ.airconnect.compatibility.domain.CompatibilityResult;

@Slf4j
@Component
public class OpenAiCompatibilityClient {

    private static final int MAX_OUTPUT_TOKENS = 120;

    private final RestClient restClient;
    private final OpenAiCompatibilityProperties properties;
    private final ObjectMapper objectMapper;

    public OpenAiCompatibilityClient(
            @Qualifier("compatibilityOpenAiRestClient") RestClient restClient,
            OpenAiCompatibilityProperties properties,
            ObjectMapper objectMapper
    ) {
        this.restClient = restClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public Optional<String> createSummary(CompatibilityResult result) {
        if (!properties.enabled() || !properties.hasApiKey()) {
            return Optional.empty();
        }

        try {
            String responseBody = restClient.post()
                    .uri("/responses")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(requestBody(result))
                    .retrieve()
                    .body(String.class);

            return extractSummary(responseBody);
        } catch (Exception e) {
            log.warn("OpenAI compatibility summary failed. model={}, reason={}",
                    properties.resolvedModel(), e.getMessage());
            return Optional.empty();
        }
    }

    private Map<String, Object> requestBody(CompatibilityResult result) {
        return Map.of(
                "model", properties.resolvedModel(),
                "input", List.of(
                        message("developer", developerPrompt()),
                        message("user", userPrompt(result))
                ),
                "text", Map.of("format", jsonSchemaFormat()),
                "reasoning", Map.of("effort", "none"),
                "max_output_tokens", MAX_OUTPUT_TOKENS
        );
    }

    private String userPrompt(CompatibilityResult result) {
        ObjectNode node = objectMapper.createObjectNode()
                .put("score", result.getScore())
                .put("grade", result.getGrade().name())
                .put("mbtiTier", result.getMbtiTier().name());
        node.set("reasons", objectMapper.valueToTree(result.getReasons()));
        node.set("cautions", objectMapper.valueToTree(result.getCautions()));
        return node.toString();
    }

    private Map<String, Object> message(String role, String text) {
        return Map.of(
                "role", role,
                "content", List.of(Map.of(
                        "type", "input_text",
                        "text", text
                ))
        );
    }

    private String developerPrompt() {
        return """
                너는 대학생 소개팅 앱의 궁합 요약 작성자다.
                입력에는 서버가 계산한 score, grade, mbtiTier, reasons, cautions만 있다.
                원본 프로필을 추측하지 말고, 점수와 제공된 이유만 근거로 삼아라.
                한국어로 1~2문장만 작성하라.
                짧고 담백하게 말하라. 과장, 운명, 확정 표현, 의학/심리 진단 표현을 쓰지 마라.
                """;
    }

    private Map<String, Object> jsonSchemaFormat() {
        return Map.of(
                "type", "json_schema",
                "name", "compatibility_summary",
                "strict", true,
                "schema", Map.of(
                        "type", "object",
                        "additionalProperties", false,
                        "required", List.of("summary"),
                        "properties", Map.of(
                                "summary", Map.of(
                                        "type", "string",
                                        "description", "2문장 이하의 짧은 한국어 연애 궁합 요약"
                                )
                        )
                )
        );
    }

    private Optional<String> extractSummary(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return Optional.empty();
        }

        try {
            JsonNode root = objectMapper.readTree(responseBody);
            Optional<String> outputText = findOutputText(root);
            if (outputText.isEmpty()) {
                return Optional.empty();
            }

            JsonNode summaryNode = objectMapper.readTree(outputText.get()).path("summary");
            if (!summaryNode.isTextual()) {
                return Optional.empty();
            }
            return Optional.of(summaryNode.asText());
        } catch (Exception e) {
            log.warn("Failed to parse OpenAI compatibility summary. reason={}", e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<String> findOutputText(JsonNode root) {
        JsonNode outputText = root.path("output_text");
        if (outputText.isTextual()) {
            return Optional.of(outputText.asText());
        }

        JsonNode output = root.path("output");
        if (!output.isArray()) {
            return Optional.empty();
        }

        for (JsonNode item : output) {
            JsonNode content = item.path("content");
            if (!content.isArray()) {
                continue;
            }

            for (JsonNode contentItem : content) {
                JsonNode text = contentItem.path("text");
                if (text.isTextual()) {
                    return Optional.of(text.asText());
                }
            }
        }

        return Optional.empty();
    }
}
