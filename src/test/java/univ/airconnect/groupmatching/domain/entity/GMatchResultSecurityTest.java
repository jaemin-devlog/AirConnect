package univ.airconnect.groupmatching.domain.entity;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import univ.airconnect.global.error.BusinessException;
import univ.airconnect.groupmatching.domain.GMatchResultStatus;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GMatchResultSecurityTest {
    @Test
    void cancelledBlockedPairCanRetryWithoutViolatingTheUniqueTeamPairConstraint() {
        var result = GMatchResult.create(1L, 2L);
        var previousMatchTime = LocalDateTime.now().minusDays(1);
        ReflectionTestUtils.setField(result, "id", 7L);
        ReflectionTestUtils.setField(result, "matchedAt", previousMatchTime);
        result.cancel();

        result.retryCancelledMatch();

        assertThat(result.getId()).isEqualTo(7L);
        assertThat(result.getStatus()).isEqualTo(GMatchResultStatus.MATCHED);
        assertThat(result.getMatchedAt()).isAfter(previousMatchTime);
        assertThat(result.getCancelledAt()).isNull();
        assertThat(result.getFinalGroupChatRoomId()).isNull();
    }

    @Test
    void completedOrStillPendingMatchCannotBeReopenedForAnotherTicketCharge() {
        var pending = GMatchResult.create(1L, 2L);
        assertThatThrownBy(pending::retryCancelledMatch).isInstanceOf(BusinessException.class);
        pending.completeFinalRoomCreation(3L);
        assertThatThrownBy(pending::retryCancelledMatch).isInstanceOf(BusinessException.class);
        assertThat(pending.getFinalGroupChatRoomId()).isEqualTo(3L);
    }
}
