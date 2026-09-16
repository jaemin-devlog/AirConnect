package univ.airconnect.iap.google;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import univ.airconnect.iap.infrastructure.IapProperties;

import java.util.List;

/** Verifies the OIDC token attached by an authenticated Google Pub/Sub push subscription. */
@Slf4j
@Component
public class GooglePubSubPushAuthenticator {

    private final GoogleIdTokenVerifier verifier;
    private final String expectedEmail;

    public GooglePubSubPushAuthenticator(IapProperties properties) {
        String audience = properties.getGoogle().getPubsubAudience();
        String serviceAccountEmail = properties.getGoogle().getPubsubServiceAccountEmail();
        this.expectedEmail = serviceAccountEmail;
        this.verifier = StringUtils.hasText(audience) && StringUtils.hasText(serviceAccountEmail)
                ? new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                        .setAudience(List.of(audience))
                        .build()
                : null;
    }

    GooglePubSubPushAuthenticator(GoogleIdTokenVerifier verifier, String expectedEmail) {
        this.verifier = verifier;
        this.expectedEmail = expectedEmail;
    }

    public boolean isAuthorized(String authorizationHeader) {
        if (verifier == null) {
            log.error("Google Pub/Sub push authentication is not configured.");
            return false;
        }
        if (!StringUtils.hasText(authorizationHeader) || !authorizationHeader.startsWith("Bearer ")) {
            return false;
        }

        String token = authorizationHeader.substring(7).trim();
        if (token.isEmpty()) {
            return false;
        }

        try {
            GoogleIdToken idToken = verifier.verify(token);
            if (idToken == null) {
                return false;
            }
            GoogleIdToken.Payload payload = idToken.getPayload();
            return Boolean.TRUE.equals(payload.getEmailVerified())
                    && expectedEmail.equalsIgnoreCase(payload.getEmail());
        } catch (Exception ex) {
            log.warn("Google Pub/Sub push authentication failed. type={}", ex.getClass().getSimpleName());
            return false;
        }
    }
}
