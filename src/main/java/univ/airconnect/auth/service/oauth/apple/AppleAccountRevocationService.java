package univ.airconnect.auth.service.oauth.apple;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import univ.airconnect.auth.domain.entity.SocialProvider;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.dto.request.DeleteAccountRequest;

@Slf4j
@Service
@RequiredArgsConstructor
public class AppleAccountRevocationService {

    private static final String TOKEN_TYPE_REFRESH = "refresh_token";
    private static final String TOKEN_TYPE_ACCESS = "access_token";

    private final AppleAuthProperties appleAuthProperties;
    private final AppleClientSecretService appleClientSecretService;
    private final AppleTokenRevocationClient appleTokenRevocationClient;

    public AppleRevocationResult revokeOnAccountDeletion(User user, DeleteAccountRequest request, String traceId) {
        if (user == null || user.getProvider() != SocialProvider.APPLE) {
            return AppleRevocationResult.skipped("NON_APPLE_USER");
        }

        if (!appleAuthProperties.getRevoke().isEnabled()) {
            log.info("Apple revoke skipped by config. traceId={}, userId={}", traceIdOrDash(traceId), user.getId());
            return AppleRevocationResult.skipped("REVOKE_DISABLED");
        }

        AppleRevokeToken targetToken = resolveToken(request);
        if (targetToken == null) {
            log.warn("Apple revoke token is missing. traceId={}, userId={}",
                    traceIdOrDash(traceId),
                    user.getId());
            return AppleRevocationResult.skipped("TOKEN_MISSING");
        }

        try {
            String clientSecret = appleClientSecretService.createClientSecret();
            appleTokenRevocationClient.revoke(targetToken.value(), targetToken.typeHint(), clientSecret);
            log.info("Apple revoke succeeded. traceId={}, userId={}, tokenSource={}, tokenMasked={}",
                    traceIdOrDash(traceId),
                    user.getId(),
                    targetToken.source(),
                    mask(targetToken.value()));
            return AppleRevocationResult.success(targetToken.source());
        } catch (Exception e) {
            log.warn("Apple revoke failed. traceId={}, userId={}, tokenSource={}, tokenMasked={}, reason={}",
                    traceIdOrDash(traceId),
                    user.getId(),
                    targetToken.source(),
                    mask(targetToken.value()),
                    e.getMessage());
            return AppleRevocationResult.failed(targetToken.source(), e.getMessage());
        }
    }

    private AppleRevokeToken resolveToken(DeleteAccountRequest request) {
        if (request == null) {
            return null;
        }
        if (hasText(request.getAppleRefreshToken())) {
            return new AppleRevokeToken(request.getAppleRefreshToken(), TOKEN_TYPE_REFRESH, "APPLE_REFRESH_TOKEN");
        }
        if (hasText(request.getAppleAccessToken())) {
            return new AppleRevokeToken(request.getAppleAccessToken(), TOKEN_TYPE_ACCESS, "APPLE_ACCESS_TOKEN");
        }
        if (hasText(request.getAppleAuthorizationCode())) {
            return new AppleRevokeToken(request.getAppleAuthorizationCode(), null, "APPLE_AUTHORIZATION_CODE");
        }
        if (hasText(request.getAppleIdentityToken())) {
            return new AppleRevokeToken(request.getAppleIdentityToken(), null, "APPLE_IDENTITY_TOKEN");
        }
        return null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String traceIdOrDash(String traceId) {
        return (traceId == null || traceId.isBlank()) ? "-" : traceId;
    }

    private String mask(String token) {
        if (token == null || token.isBlank()) {
            return "***";
        }
        int length = token.length();
        if (length <= 8) {
            return "***";
        }
        return token.substring(0, 4) + "..." + token.substring(length - 4);
    }

    private record AppleRevokeToken(String value, String typeHint, String source) {
    }

    public record AppleRevocationResult(boolean attempted, boolean success, String source, String reason) {
        public static AppleRevocationResult skipped(String reason) {
            return new AppleRevocationResult(false, true, null, reason);
        }

        public static AppleRevocationResult success(String source) {
            return new AppleRevocationResult(true, true, source, null);
        }

        public static AppleRevocationResult failed(String source, String reason) {
            return new AppleRevocationResult(true, false, source, reason);
        }
    }
}
