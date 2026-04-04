package univ.airconnect.ads.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import univ.airconnect.ads.domain.entity.AdRewardSession;
import univ.airconnect.ads.dto.response.AdRewardSessionCreateResponse;
import univ.airconnect.ads.exception.AdsErrorCode;
import univ.airconnect.ads.exception.AdsException;
import univ.airconnect.ads.repository.AdRewardSessionRepository;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

@Service
@Slf4j
public class AdRewardSessionService {

    private static final char[] ALPHANUM = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AdRewardSessionRepository adRewardSessionRepository;

    @Value("${ads.reward.default-ticket:1}")
    private int defaultRewardAmount;

    @Value("${ads.reward.session-ttl-minutes:10}")
    private int sessionTtlMinutes;

    @Value("${ads.reward.daily-limit:10}")
    private int dailyLimit;

    @Value("${ads.reward.daily-limit-zone:Asia/Seoul}")
    private String dailyLimitZone;

    public AdRewardSessionService(AdRewardSessionRepository adRewardSessionRepository) {
        this.adRewardSessionRepository = adRewardSessionRepository;
    }

    @Transactional
    public AdRewardSessionCreateResponse createSession(Long userId) {
        ZoneId zoneId = ZoneId.of(dailyLimitZone);
        LocalDate today = LocalDate.now(zoneId);
        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = today.plusDays(1).atStartOfDay();

        long rewardedToday = adRewardSessionRepository.countRewardedToday(userId, start, end);
        if (rewardedToday >= dailyLimit) {
            throw new AdsException(AdsErrorCode.AD_REWARD_DAILY_LIMIT_EXCEEDED);
        }

        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(sessionTtlMinutes);
        String sessionKey = generateSessionKey();

        AdRewardSession session = adRewardSessionRepository.save(
                AdRewardSession.createReady(sessionKey, userId, defaultRewardAmount, expiresAt)
        );

        log.info("Ad reward session created. userId={}, sessionId={}, sessionKey={}, expiresAt={}",
                userId, session.getId(), session.getSessionKey(), session.getExpiresAt());

        return AdRewardSessionCreateResponse.builder()
                .sessionKey(session.getSessionKey())
                .rewardAmount(session.getRewardAmount())
                .expiresAt(OffsetDateTime.of(session.getExpiresAt(), ZoneOffset.UTC))
                .build();
    }

    private String generateSessionKey() {
        StringBuilder sb = new StringBuilder(40);
        for (int i = 0; i < 40; i++) {
            sb.append(ALPHANUM[RANDOM.nextInt(ALPHANUM.length)]);
        }
        return sb.toString();
    }
}

