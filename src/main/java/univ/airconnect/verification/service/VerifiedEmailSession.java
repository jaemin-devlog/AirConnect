package univ.airconnect.verification.service;

import java.time.OffsetDateTime;

public record VerifiedEmailSession(
        String email,
        String verificationToken,
        OffsetDateTime expiresAt
) {
}

