package univ.airconnect.global.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import univ.airconnect.auth.exception.AuthException;
import univ.airconnect.auth.security.AccessTokenRevocationService;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtProviderTest {
    private static final String TEST_SECRET = "test-only-secret-with-at-least-32-bytes-not-production";
    private final AccessTokenRevocationService revocations = mock(AccessTokenRevocationService.class);
    private final JwtProvider provider = new JwtProvider(new JwtProperties(TEST_SECRET, 3600, 2592000), revocations);

    @Test
    void tokensIssuedInSameSecondAreUniqueAndOldClientClaimsRemainPresent() {
        Set<String> refreshTokens = new HashSet<>();
        Set<String> accessTokens = new HashSet<>();
        for (int i = 0; i < 30; i++) {
            refreshTokens.add(provider.createRefreshToken(15L, "test-device"));
            accessTokens.add(provider.createAccessToken(15L, "test-device", "test-session"));
        }
        assertThat(refreshTokens).hasSize(30);
        assertThat(accessTokens).hasSize(30);
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8)))
                .build().parseClaimsJws(refreshTokens.iterator().next()).getBody();
        assertThat(claims.getId()).isNotBlank();
        assertThat(claims.getSubject()).isEqualTo("15");
        assertThat(claims.get("deviceId")).isEqualTo("test-device");
        assertThat(claims.get("type")).isEqualTo("refresh");
    }

    @Test
    void loggedOutAccessTokenIsRejectedWhileAnotherTokenRemainsValid() {
        String revoked = provider.createAccessToken(15L, "test-device", "test-session");
        String active = provider.createAccessToken(15L, "test-device", "test-session");
        when(revocations.isSessionActive(15L, "test-device", "test-session")).thenReturn(true);
        when(revocations.isRevokedFingerprint(AccessTokenRevocationService.fingerprint(revoked))).thenReturn(true);

        assertThatThrownBy(() -> provider.validateAccessToken(revoked)).isInstanceOf(AuthException.class);
        provider.validateAccessToken(active);
        assertThat(provider.getAccessTokenExpiresAt(active)).isAfter(Instant.now());
    }

    @Test
    void existingSignedTokensWithoutJtiRemainValid() {
        String legacy = Jwts.builder().setSubject("15").claim("type", "access")
                .setExpiration(java.util.Date.from(Instant.now().plusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
        provider.validateAccessToken(legacy);
        assertThat(provider.getUserId(legacy)).isEqualTo(15L);
    }

    @Test
    void logoutInvalidatesAllTokensForDeviceSessionAndDoesNotAffectAnotherDevice() {
        String beforeRefresh = provider.createAccessToken(15L, "device-a", "session-a");
        String afterRefresh = provider.createAccessToken(15L, "device-a", "session-a");
        String otherDevice = provider.createAccessToken(15L, "device-b", "session-b");
        when(revocations.isSessionActive(15L, "device-a", "session-a")).thenReturn(true);
        when(revocations.isSessionActive(15L, "device-b", "session-b")).thenReturn(true);
        provider.validateAccessToken(beforeRefresh);
        provider.validateAccessToken(afterRefresh);

        when(revocations.isSessionActive(15L, "device-a", "session-a")).thenReturn(false);

        assertThatThrownBy(() -> provider.validateAccessToken(beforeRefresh)).isInstanceOf(AuthException.class);
        assertThatThrownBy(() -> provider.validateAccessToken(afterRefresh)).isInstanceOf(AuthException.class);
        provider.validateAccessToken(otherDevice);
    }

    @Test
    void reloginDoesNotReviveAccessTokensFromPreviousSession() {
        String previous = provider.createAccessToken(15L, "device-a", "previous-session");
        String current = provider.createAccessToken(15L, "device-a", "new-session");
        when(revocations.isSessionActive(15L, "device-a", "new-session")).thenReturn(true);

        assertThatThrownBy(() -> provider.validateAccessToken(previous)).isInstanceOf(AuthException.class);
        provider.validateAccessToken(current);
        assertThat(provider.getSessionId(current)).isEqualTo("new-session");
        assertThat(provider.getDeviceId(current)).isEqualTo("device-a");
    }
}
