package univ.airconnect.compatibility.service;

import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import univ.airconnect.compatibility.domain.CompatibilityGrade;
import univ.airconnect.compatibility.domain.CompatibilityResult;
import univ.airconnect.compatibility.infrastructure.OpenAiCompatibilityClient;

@Service
@RequiredArgsConstructor
public class CompatibilitySummaryService {

    private static final int SUMMARY_MAX_LENGTH = 180;

    private final OpenAiCompatibilityClient openAiCompatibilityClient;

    public String summarize(CompatibilityResult result) {
        return openAiCompatibilityClient.createSummary(result)
                .map(this::sanitize)
                .filter(summary -> !summary.isBlank())
                .orElseGet(() -> fallbackSummary(result));
    }

    private String fallbackSummary(CompatibilityResult result) {
        String lead = result.getGrade() == CompatibilityGrade.LOW
                ? "두 분은 천천히 속도를 맞춰보면 좋아요."
                : "두 분은 공통점을 시작점으로 대화를 이어가기 좋은 조합이에요.";

        String reason = result.getReasons().isEmpty()
                ? "가벼운 질문부터 서로의 생활 리듬을 확인해 보세요."
                : result.getReasons().get(0);

        return sanitize(lead + " " + reason);
    }

    private String sanitize(String summary) {
        String trimmed = Optional.ofNullable(summary).orElse("").trim().replaceAll("\\s+", " ");
        String limitedSentences = limitSentences(trimmed);
        if (limitedSentences.length() <= SUMMARY_MAX_LENGTH) {
            return limitedSentences;
        }
        return limitedSentences.substring(0, SUMMARY_MAX_LENGTH).trim();
    }

    private String limitSentences(String summary) {
        int sentenceCount = 0;
        for (int i = 0; i < summary.length(); i++) {
            char ch = summary.charAt(i);
            if (ch == '.' || ch == '!' || ch == '?') {
                sentenceCount++;
                if (sentenceCount >= 2) {
                    return summary.substring(0, i + 1).trim();
                }
            }
        }
        return summary;
    }
}
