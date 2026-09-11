package univ.airconnect.referral.dto.response;

import java.time.LocalDateTime;

public record ReferralRedeemResponse(
        String referralCode,
        int rewardTickets,
        int myTickets,
        boolean firstApplied,
        LocalDateTime redeemedAt
) {
}
