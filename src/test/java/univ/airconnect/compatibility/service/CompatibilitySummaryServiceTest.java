package univ.airconnect.compatibility.service;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import univ.airconnect.compatibility.domain.CompatibilityGrade;
import univ.airconnect.compatibility.domain.CompatibilityResult;
import univ.airconnect.compatibility.domain.MbtiCompatibilityTier;
import univ.airconnect.compatibility.infrastructure.OpenAiCompatibilityClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CompatibilitySummaryServiceTest {

    @Test
    void summarize_returnsFallback_whenOpenAiClientReturnsEmpty() {
        OpenAiCompatibilityClient client = mock(OpenAiCompatibilityClient.class);
        CompatibilitySummaryService service = new CompatibilitySummaryService(client);
        CompatibilityResult result = CompatibilityResult.builder()
                .myUserId(1L)
                .targetUserId(2L)
                .score(88)
                .grade(CompatibilityGrade.AMAZING)
                .mbtiTier(MbtiCompatibilityTier.IDEAL)
                .reasons(List.of("MBTI 흐름이 잘 맞아 편하게 대화를 이어가기 좋은 조합이에요."))
                .cautions(List.of("생활 패턴은 직접 확인해 보세요."))
                .build();

        when(client.createSummary(result)).thenReturn(Optional.empty());

        String summary = service.summarize(result);

        assertThat(summary).contains("공통점");
        assertThat(summary).contains("대화를 이어가기 좋은 조합");
    }
}
