package univ.airconnect.global.security.stomp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import univ.airconnect.auth.security.AccessTokenRevocationService;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StompSessionRegistryTest {

    private StompSessionRegistry registry;
    private AccessTokenRevocationService revocations;

    @BeforeEach
    void setUp() {
        revocations = mock(AccessTokenRevocationService.class);
        registry = new StompSessionRegistry(revocations);
    }

    @Test
    void countsDistinctUsersAcrossMultipleSessions() {
        register("user1-phone", 1L);
        assertThat(registry.onlineUserCount()).isEqualTo(1);
        register("user1-tablet", 1L);
        assertThat(registry.onlineUserCount()).isEqualTo(1);

        register("user2-phone", 2L);
        assertThat(registry.onlineUserCount()).isEqualTo(2);

        registry.remove("user1-phone");
        assertThat(registry.onlineUserCount()).isEqualTo(2);

        registry.remove("user1-tablet");
        assertThat(registry.onlineUserCount()).isEqualTo(1);
    }

    @Test
    void revokeUserRemovesAllSessions() {
        register("user1-phone", 1L);
        register("user1-tablet", 1L);
        register("user2-phone", 2L);
        assertThat(registry.revokeUser(1L)).isEqualTo(2);

        assertThat(registry.onlineUserCount()).isEqualTo(1);
    }

    @Test
    void expiredTokenCannotRegisterOrAuthorizeSocket() {
        registry.register("expired", 1L, Instant.now().minusSeconds(1), "expired-fingerprint");
        assertThat(registry.isAuthorized("expired", 1L)).isFalse();
        assertThat(registry.findUserId("expired")).isEmpty();
    }

    @Test
    void revocationOnAnotherApplicationInstanceStopsExistingSocket() {
        register("connected", 1L);
        assertThat(registry.isAuthorized("connected", 1L)).isTrue();
        when(revocations.isRevokedFingerprint("connected")).thenReturn(true);
        assertThat(registry.findUserId("connected")).isEmpty();
        assertThat(registry.isAuthorized("connected", 1L)).isFalse();
    }

    @Test
    void revocationStoreOutageDoesNotAuthorizeProtectedFrames() {
        register("connected", 1L);
        when(revocations.isRevokedFingerprint("connected")).thenThrow(new IllegalStateException("unavailable"));
        assertThat(registry.findUserId("connected")).isEmpty();
    }

    @Test
    void logoutOrNewLoginInvalidatesAllTokensFromTheSameSession() {
        registry.register("older-access-token", 1L, Instant.now().plusSeconds(3600), "older-token",
                "phone", "login-session");
        when(revocations.isSessionActive(1L, "phone", "login-session")).thenReturn(true);
        assertThat(registry.isAuthorized("older-access-token", 1L)).isTrue();

        // Deleting/replacing the refresh session also denies older, non-blacklisted access tokens.
        when(revocations.isSessionActive(1L, "phone", "login-session")).thenReturn(false);
        assertThat(registry.findUserId("older-access-token")).isEmpty();
    }

    @Test
    void legacyAccessTokenWithoutSessionClaimRemainsValidUntilExpiryOrRevocation() {
        register("legacy", 1L);
        assertThat(registry.isAuthorized("legacy", 1L)).isTrue();
        org.mockito.Mockito.verify(revocations, org.mockito.Mockito.never())
                .isSessionActive(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any());
    }

    private void register(String sessionId, Long userId) {
        registry.register(sessionId, userId, Instant.now().plusSeconds(3600), sessionId);
    }
}
