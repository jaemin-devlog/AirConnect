package univ.airconnect.iap.google;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import org.junit.jupiter.api.Test;
import univ.airconnect.iap.infrastructure.IapProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GooglePubSubPushAuthenticatorTest {

    @Test
    void failsClosedWhenExpectedOidcIdentityIsNotConfigured() {
        GooglePubSubPushAuthenticator authenticator =
                new GooglePubSubPushAuthenticator(new IapProperties());

        assertThat(authenticator.isAuthorized("Bearer dummy-token")).isFalse();
    }

    @Test
    void rejectsMissingBearerTokenWithoutRemoteVerification() {
        IapProperties properties = new IapProperties();
        properties.getGoogle().setPubsubAudience("https://example.test/iap/android/notifications");
        properties.getGoogle().setPubsubServiceAccountEmail("pubsub@example.test");
        GooglePubSubPushAuthenticator authenticator = new GooglePubSubPushAuthenticator(properties);

        assertThat(authenticator.isAuthorized(null)).isFalse();
        assertThat(authenticator.isAuthorized("Basic dummy")).isFalse();
    }

    @Test
    void acceptsOnlyVerifiedConfiguredServiceAccountIdentity() throws Exception {
        GoogleIdTokenVerifier verifier = mock(GoogleIdTokenVerifier.class);
        GoogleIdToken token = mock(GoogleIdToken.class);
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload()
                .setEmail("pubsub@example.test")
                .setEmailVerified(true);
        when(verifier.verify("valid-dummy-token")).thenReturn(token);
        when(token.getPayload()).thenReturn(payload);
        GooglePubSubPushAuthenticator authenticator =
                new GooglePubSubPushAuthenticator(verifier, "pubsub@example.test");

        assertThat(authenticator.isAuthorized("Bearer valid-dummy-token")).isTrue();

        payload.setEmail("other@example.test");
        assertThat(authenticator.isAuthorized("Bearer valid-dummy-token")).isFalse();

        payload.setEmail("pubsub@example.test").setEmailVerified(false);
        assertThat(authenticator.isAuthorized("Bearer valid-dummy-token")).isFalse();
    }
}
