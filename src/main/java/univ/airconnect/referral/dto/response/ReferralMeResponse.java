package univ.airconnect.referral.dto.response;

public record ReferralMeResponse(
        String referralCode,
        int rewardTickets,
        int milestoneInterval,
        int milestoneBonusTickets,
        long referredFriendCount,
        long nextMilestoneAt,
        int remainingForNextMilestone,
        boolean hasEnteredReferralCode,
        boolean canEnterReferralCode
) {
}
