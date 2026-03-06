package univ.airconnect.auth.dto.response;

public record TokenPairResponse(
        String accessToken,
        String refreshToken
) {}