package univ.airconnect.compatibility.service;

import org.junit.jupiter.api.Test;
import univ.airconnect.global.error.BusinessException;
import univ.airconnect.global.error.ErrorCode;
import univ.airconnect.moderation.service.UserBlockPolicyService;
import univ.airconnect.user.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class CompatibilityBlockSecurityTest {
    @Test
    void blockedPairCannotReadTraitsOrTriggerExternalSummary() {
        UserRepository users = mock(UserRepository.class);
        CompatibilityScoreCalculator calculator = mock(CompatibilityScoreCalculator.class);
        CompatibilitySummaryService summaries = mock(CompatibilitySummaryService.class);
        UserBlockPolicyService blocks = mock(UserBlockPolicyService.class);
        CompatibilityService service = new CompatibilityService(users, calculator, summaries, blocks);
        when(blocks.hasBlockRelation(1L, 2L)).thenReturn(true);

        assertThatThrownBy(() -> service.getCompatibility(1L, 2L))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> org.assertj.core.api.Assertions.assertThat(error.getErrorCode())
                                .isEqualTo(ErrorCode.USER_BLOCKED_INTERACTION));
        verifyNoInteractions(users, calculator, summaries);
    }
}
