package univ.airconnect.verification.service;

import univ.airconnect.verification.domain.VerificationNextAction;

import java.time.OffsetDateTime;

public record VerifiedEmailSession(
        String email,
        String verificationToken,
        OffsetDateTime expiresAt,
        VerificationNextAction nextAction
) {
}
