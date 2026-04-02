package univ.airconnect.ads.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import univ.airconnect.ads.dto.response.AdRewardSessionCreateResponse;
import univ.airconnect.ads.exception.AdsErrorCode;
import univ.airconnect.ads.exception.AdsException;
import univ.airconnect.ads.repository.AdRewardSessionRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdRewardSessionServiceTest {

    @Mock
    private AdRewardSessionRepository adRewardSessionRepository;

    @InjectMocks
    private AdRewardSessionService adRewardSessionService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(adRewardSessionService, "defaultRewardAmount", 2);
        ReflectionTestUtils.setField(adRewardSessionService, "sessionTtlMinutes", 10);
        ReflectionTestUtils.setField(adRewardSessionService, "dailyLimit", 10);
        ReflectionTestUtils.setField(adRewardSessionService, "dailyLimitZone", "Asia/Seoul");
    }

    @Test
    void createSession_success() {
        when(adRewardSessionRepository.countRewardedToday(any(), any(), any())).thenReturn(0L);
        when(adRewardSessionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        AdRewardSessionCreateResponse response = adRewardSessionService.createSession(1L);

        assertThat(response.getSessionKey()).isNotBlank();
        assertThat(response.getRewardAmount()).isEqualTo(2);
        assertThat(response.getExpiresAt()).isNotNull();
    }

    @Test
    void createSession_fail_whenDailyLimitExceeded() {
        when(adRewardSessionRepository.countRewardedToday(any(), any(), any())).thenReturn(10L);

        assertThatThrownBy(() -> adRewardSessionService.createSession(1L))
                .isInstanceOf(AdsException.class)
                .extracting(ex -> ((AdsException) ex).getErrorCode())
                .isEqualTo(AdsErrorCode.AD_REWARD_DAILY_LIMIT_EXCEEDED);
    }
}


