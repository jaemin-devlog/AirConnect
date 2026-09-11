package univ.airconnect.referral.dto.response;

public record ReferralMeResponse(
        String referralCode,
        int rewardTickets,
        long referredFriendCount,
        boolean hasEnteredReferralCode,
        boolean canEnterReferralCode
) {
}
