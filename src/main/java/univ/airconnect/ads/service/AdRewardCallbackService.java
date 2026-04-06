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
        String signature = nullSafe(request.getParameter("signature"));
        String keyId = nullSafe(request.getParameter("key_id"));

        log.info("Ad reward callback inbound. txId={}, sessionKeyMasked={}, hasSignature={}, hasKeyId={}, queryLength={}",
                mask(transactionId),
                mask(sessionKey),
                !signature.isBlank(),
                !keyId.isBlank(),
                rawQuery.length());

        // AdMob 콘솔 URL 확인(probe) 또는 세션 없는 호출은 무해하게 200 응답
        if (sessionKey.isBlank()) {
            try {
                adRewardCallbackRepository.save(
                        AdRewardCallback.of(
                                null,
                                transactionId.isBlank() ? null : transactionId,
                                truncate(rawQuery),
                                false
                        )
                );
            } catch (Exception e) {
                log.error("Failed to save AdRewardCallback log for probe request", e);
            }
            return ignoredResponse("", transactionId);
        }

        // 실제 세션은 있지만 서명 메타데이터가 없으면 지급하지 않고 무시
        if (signature.isBlank() || keyId.isBlank()) {
            log.warn("Ad reward callback ignored due to missing signature metadata. txId={}, sessionKeyMasked={}",
                    mask(transactionId), mask(sessionKey));
            try {
                adRewardCallbackRepository.save(
                        AdRewardCallback.of(
                                sessionKey,
                                transactionId.isBlank() ? null : transactionId,
                                truncate(rawQuery),
                                false
                        )
                );
            } catch (Exception e) {
                log.error("Failed to save AdRewardCallback log for missing signature metadata", e);
            }
            return ignoredResponse(sessionKey, transactionId);
        }

        boolean signatureValid = admobSignatureVerifier.verify(request);

        log.info("Ad reward callback signature verification result. txId={}, sessionKeyMasked={}, valid={}",
                mask(transactionId),
                mask(sessionKey),
                signatureValid);

        try {
            adRewardCallbackRepository.save(
                    AdRewardCallback.of(
                            sessionKey,
                            transactionId.isBlank() ? null : transactionId,
                            truncate(rawQuery),
                            signatureValid
                    )
            );
        } catch (Exception e) {
            log.error("Failed to save AdRewardCallback verification result", e);
        }

        if (!signatureValid) {
            throw new AdsException(AdsErrorCode.AD_REWARD_INVALID_SIGNATURE);
        }

        if (transactionId.isBlank()) {
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

    private AdRewardCallbackResponse ignoredResponse(String sessionKey, String transactionId) {
        return AdRewardCallbackResponse.builder()
                .sessionKey(sessionKey)
                .transactionId(transactionId)
                .grantStatus("IGNORED")
                .grantedTickets(0)
                .beforeTickets(null)
                .afterTickets(null)
                .ledgerId(null)
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

    private String mask(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        if (value.length() <= 6) {
            return "***";
        }
        return value.substring(0, 3) + "***" + value.substring(value.length() - 3);
    }
}