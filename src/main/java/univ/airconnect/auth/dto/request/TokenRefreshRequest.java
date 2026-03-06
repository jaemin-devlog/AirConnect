package univ.airconnect.auth.dto.request;

public record TokenRefreshRequest(
        String refreshToken,
        String deviceId
) {}
