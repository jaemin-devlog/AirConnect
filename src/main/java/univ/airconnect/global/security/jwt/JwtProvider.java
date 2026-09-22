package univ.airconnect.global.security.jwt;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import io.jsonwebtoken.*;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.security.Keys;
import univ.airconnect.auth.exception.AuthErrorCode;
import univ.airconnect.auth.exception.AuthException;
import univ.airconnect.auth.security.AccessTokenRevocationService;

@Component
public class JwtProvider {

    private static final String CLAIM_TYPE = "type";
    private static final String CLAIM_DEVICE_ID = "deviceId";
    private static final String CLAIM_SESSION_ID = "sid";
    private static final String TOKEN_TYPE_ACCESS = "access";
    private static final String TOKEN_TYPE_REFRESH = "refresh";

    private final JwtProperties jwtProperties;
    private final SecretKey secretKey;
    private final AccessTokenRevocationService accessTokenRevocationService;

    public JwtProvider(JwtProperties jwtProperties, AccessTokenRevocationService accessTokenRevocationService) {
        this.jwtProperties = jwtProperties;
        this.secretKey = Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8));
        this.accessTokenRevocationService = accessTokenRevocationService;
    }

    public String createAccessToken(Long userId, String deviceId, String sessionId) {
        if (userId == null || deviceId == null || deviceId.isBlank() || sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("Access token requires a device login session");
        }
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(jwtProperties.accessTokenExpirationSeconds());

        return Jwts.builder()
                .setId(UUID.randomUUID().toString())
                .setSubject(String.valueOf(userId))
                .claim(CLAIM_TYPE, TOKEN_TYPE_ACCESS)
                .claim(CLAIM_DEVICE_ID, deviceId)
                .claim(CLAIM_SESSION_ID, sessionId)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(expiry))
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public String createRefreshToken(Long userId, String deviceId) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(jwtProperties.refreshTokenExpirationSeconds());

        return Jwts.builder()
                .setId(UUID.randomUUID().toString())
                .setSubject(String.valueOf(userId))
                .claim(CLAIM_TYPE, TOKEN_TYPE_REFRESH)
                .claim(CLAIM_DEVICE_ID, deviceId)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(expiry))
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public void validateAccessToken(String token) {
        Claims claims = parseClaimsWithExceptionHandling(token);
        String type = claims.get(CLAIM_TYPE, String.class);

        if (!TOKEN_TYPE_ACCESS.equals(type)) {
            throw new AuthException(AuthErrorCode.INVALID_ACCESS_TOKEN_TYPE);
        }
        if (accessTokenRevocationService.isRevokedFingerprint(AccessTokenRevocationService.fingerprint(token))) {
            throw new AuthException(AuthErrorCode.INVALID_TOKEN);
        }
        String sessionId = claims.get(CLAIM_SESSION_ID, String.class);
        // Already-issued legacy tokens have no session claim and remain valid until their original expiry.
        if (sessionId != null && !accessTokenRevocationService.isSessionActive(
                getUserId(token), claims.get(CLAIM_DEVICE_ID, String.class), sessionId)) {
            throw new AuthException(AuthErrorCode.INVALID_TOKEN);
        }
    }

    public void validateRefreshToken(String token) {
        Claims claims = parseClaimsWithExceptionHandling(token);
        String type = claims.get(CLAIM_TYPE, String.class);

        if (!TOKEN_TYPE_REFRESH.equals(type)) {
            throw new AuthException(AuthErrorCode.INVALID_REFRESH_TOKEN_TYPE);
        }
    }

    public Long getUserId(String token) {
        Claims claims = parseClaimsWithExceptionHandling(token);
        try {
            return Long.valueOf(claims.getSubject());
        } catch (NumberFormatException e) {
            throw new AuthException(AuthErrorCode.INVALID_TOKEN, e);
        }
    }

    public Instant getAccessTokenExpiresAt(String token) {
        Claims claims = parseClaimsWithExceptionHandling(token);
        if (!TOKEN_TYPE_ACCESS.equals(claims.get(CLAIM_TYPE, String.class)) || claims.getExpiration() == null) {
            throw new AuthException(AuthErrorCode.INVALID_TOKEN);
        }
        return claims.getExpiration().toInstant();
    }

    public String getDeviceId(String token) {
        Claims claims = parseClaimsWithExceptionHandling(token);
        return claims.get(CLAIM_DEVICE_ID, String.class);
    }

    public String getSessionId(String token) {
        return parseClaimsWithExceptionHandling(token).get(CLAIM_SESSION_ID, String.class);
    }

    public boolean isRefreshToken(String token) {
        Claims claims = parseClaimsWithExceptionHandling(token);
        String type = claims.get(CLAIM_TYPE, String.class);
        return TOKEN_TYPE_REFRESH.equals(type);
    }

    private Claims parseClaimsWithExceptionHandling(String token) {
        try {
            return parseClaims(token);
        } catch (ExpiredJwtException e) {
            throw new AuthException(AuthErrorCode.TOKEN_EXPIRED, e);
        } catch (JwtException | IllegalArgumentException e) {
            throw new AuthException(AuthErrorCode.INVALID_TOKEN, e);
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
