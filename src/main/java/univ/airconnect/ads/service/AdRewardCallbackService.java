package univ.airconnect.ads.service;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import univ.airconnect.ads.domain.entity.AdRewardCallback;
import univ.airconnect.ads.domain.entity.AdRewardSession;
import univ.airconnect.ads.dto.response.AdRewardCallbackResponse;
import univ.airconnect.ads.exception.AdsErrorCode;
import univ.airconnect.ads.exception.AdsException;
import univ.airconnect.ads.infrastructure.AdmobSignatureVerifier;
import univ.airconnect.ads.repository.AdRewardCallbackRepository;
import univ.airconnect.ads.repository.AdRewardSessionRepository;
import univ.airconnect.ticket.service.AdTicketGrantService;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Service
@Slf4j
public class AdRewardCallbackService {

    private final AdmobSignatureVerifier admobSignatureVerifier;
    private final AdRewardSessionRepository adRewardSessionRepository;
    private final AdRewardCallbackRepository adRewardCallbackRepository;
    private final AdTicketGrantService adTicketGrantService;

    public AdRewardCallbackService(AdmobSignatureVerifier admobSignatureVerifier,
                                   AdRewardSessionRepository adRewardSessionRepository,
                                   AdRewardCallbackRepository adRewardCallbackRepository,
                                   AdTicketGrantService adTicketGrantService) {
        this.admobSignatureVerifier = admobSignatureVerifier;
        this.adRewardSessionRepository = adRewardSessionRepository;
        this.adRewardCallbackRepository = adRewardCallbackRepository;
        this.adTicketGrantService = adTicketGrantService;
    }

    @Transactional
    public AdRewardCallbackResponse handleAdmobCallback(HttpServletRequest request) {
        String rawQuery = request.getQueryString() == null ? "" : request.getQueryString();
        String transactionId = nullSafe(request.getParameter("transaction_id"));
        String customData = nullSafe(request.getParameter("custom_data"));
        String sessionKey = extractSessionKey(customData);

        // AdMob 콘솔 URL 확인 요청은 핵심 파라미터 없이 들어올 수 있으므로 무해하게 200 처리한다.
        if (sessionKey.isBlank() && transactionId.isBlank()
                && nullSafe(request.getParameter("signature")).isBlank()
                && nullSafe(request.getParameter("key_id")).isBlank()) {
            return AdRewardCallbackResponse.builder()
                    .sessionKey("")
                    .transactionId("")
                    .grantStatus("IGNORED")
                    .grantedTickets(0)
                    .beforeTickets(null)
                    .afterTickets(null)
                    .ledgerId(null)
                    .processedAt(OffsetDateTime.now(ZoneOffset.UTC))
                    .build();
        }

        boolean signatureValid = admobSignatureVerifier.verify(request);
        adRewardCallbackRepository.save(AdRewardCallback.of(sessionKey, transactionId, truncate(rawQuery), signatureValid));

        if (!signatureValid) {
            throw new AdsException(AdsErrorCode.AD_REWARD_INVALID_SIGNATURE);
        }
        if (sessionKey.isBlank() || transactionId.isBlank()) {
            throw new AdsException(AdsErrorCode.AD_REWARD_INVALID_CALLBACK);
        }

        AdRewardSession session = adRewardSessionRepository.findBySessionKeyForUpdate(sessionKey)
                .orElseThrow(() -> new AdsException(AdsErrorCode.AD_REWARD_INVALID_SESSION));

        if (session.isRewarded()) {
            AdTicketGrantService.GrantResult already = adTicketGrantService.grantFromAdReward(
                    session.getUserId(), session.getRewardAmount(), session.getId()
            );
            return AdRewardCallbackResponse.builder()
                    .sessionKey(sessionKey)
                    .transactionId(session.getTransactionId())
                    .grantStatus("ALREADY_GRANTED")
                    .grantedTickets(session.getRewardAmount())
                    .beforeTickets(already.beforeTickets())
                    .afterTickets(already.afterTickets())
                    .ledgerId(already.ledgerExternalId())
                    .processedAt(OffsetDateTime.now(ZoneOffset.UTC))
                    .build();
        }

        if (!session.isReady()) {
            throw new AdsException(AdsErrorCode.AD_REWARD_INVALID_SESSION);
        }

        if (session.isExpired(LocalDateTime.now())) {
            session.markExpired();
            throw new AdsException(AdsErrorCode.AD_REWARD_SESSION_EXPIRED);
        }

        if (adRewardSessionRepository.existsByTransactionId(transactionId)) {
            throw new AdsException(AdsErrorCode.AD_REWARD_DUPLICATE_TRANSACTION);
        }

        AdTicketGrantService.GrantResult grantResult = adTicketGrantService.grantFromAdReward(
                session.getUserId(),
                session.getRewardAmount(),
                session.getId()
        );

        session.markRewarded(transactionId);

        log.info("Ad reward callback processed. sessionId={}, userId={}, txId={}, granted={}, before={}, after={}",
                session.getId(), session.getUserId(), transactionId,
                grantResult.granted(), grantResult.beforeTickets(), grantResult.afterTickets());

        return AdRewardCallbackResponse.builder()
                .sessionKey(sessionKey)
                .transactionId(transactionId)
                .grantStatus(grantResult.granted() ? "GRANTED" : "ALREADY_GRANTED")
                .grantedTickets(session.getRewardAmount())
                .beforeTickets(grantResult.beforeTickets())
                .afterTickets(grantResult.afterTickets())
                .ledgerId(grantResult.ledgerExternalId())
                .processedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();
    }

    private String extractSessionKey(String customData) {
        if (customData == null || customData.isBlank()) {
            return "";
        }
        if (customData.startsWith("sessionKey=")) {
            return customData.substring("sessionKey=".length()).trim();
        }
        return customData.trim();
    }

    private String nullSafe(String value) {
        return value == null ? "" : value.trim();
    }

    private String truncate(String value) {
        if (value == null) {
            return "";
        }
        if (value.length() <= 2000) {
            return value;
        }
        return value.substring(0, 2000);
    }
}


