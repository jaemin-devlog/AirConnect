package univ.airconnect.global.security.jwt;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import io.jsonwebtoken.*;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.security.Keys;
import univ.airconnect.auth.exception.AuthErrorCode;
import univ.airconnect.auth.exception.AuthException;

@Component
public class JwtProvider {

    private static final String CLAIM_TYPE = "type";
    private static final String CLAIM_DEVICE_ID = "deviceId";
    private static final String TOKEN_TYPE_ACCESS = "access";
    private static final String TOKEN_TYPE_REFRESH = "refresh";

    private final JwtProperties jwtProperties;
    private final SecretKey secretKey;

    public JwtProvider(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        this.secretKey = Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String createAccessToken(Long userId) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(jwtProperties.accessTokenExpirationSeconds());

        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .claim(CLAIM_TYPE, TOKEN_TYPE_ACCESS)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(expiry))
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public String createRefreshToken(Long userId, String deviceId) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(jwtProperties.refreshTokenExpirationSeconds());

        return Jwts.builder()
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

    public String getDeviceId(String token) {
        Claims claims = parseClaimsWithExceptionHandling(token);
        return claims.get(CLAIM_DEVICE_ID, String.class);
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