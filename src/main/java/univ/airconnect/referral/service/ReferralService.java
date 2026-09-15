package univ.airconnect.referral.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import univ.airconnect.global.error.BusinessException;
import univ.airconnect.global.error.ErrorCode;
import univ.airconnect.iap.domain.entity.TicketLedger;
import univ.airconnect.iap.repository.TicketLedgerRepository;
import univ.airconnect.notification.domain.NotificationType;
import univ.airconnect.notification.service.NotificationService;
import univ.airconnect.referral.domain.entity.ReferralCode;
import univ.airconnect.referral.domain.entity.ReferralRedemption;
import univ.airconnect.referral.dto.response.ReferralMeResponse;
import univ.airconnect.referral.dto.response.ReferralRedeemResponse;
import univ.airconnect.referral.repository.ReferralCodeRepository;
import univ.airconnect.referral.repository.ReferralRedemptionRepository;
import univ.airconnect.user.domain.OnboardingStatus;
import univ.airconnect.user.domain.UserStatus;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.repository.UserRepository;

import java.security.SecureRandom;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ReferralService {

    private static final char[] CODE_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();
    private static final int CODE_LENGTH = 6;
    private static final int CODE_GENERATION_ATTEMPTS = 20;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ReferralCodeRepository referralCodeRepository;
    private final ReferralRedemptionRepository referralRedemptionRepository;
    private final UserRepository userRepository;
    private final TicketLedgerRepository ticketLedgerRepository;
    private final NotificationService notificationService;

    @Value("${app.rewards.referral-base-tickets:3}")
    private int baseRewardTickets;

    @Value("${app.rewards.referral-milestone-interval:10}")
    private int milestoneInterval;

    @Value("${app.rewards.referral-milestone-bonus-tickets:5}")
    private int milestoneBonusTickets;

    @Transactional
    public ReferralMeResponse getMe(Long userId) {
        User user = lockEligibleUser(userId);
        ReferralCode referralCode = referralCodeRepository.findByUserId(userId)
                .orElseGet(() -> referralCodeRepository.save(ReferralCode.issue(user.getId(), generateUniqueCode())));
        boolean hasEntered = referralRedemptionRepository.findByReferredUserId(userId).isPresent();

        long referredFriendCount = referralRedemptionRepository.countByReferrerUserId(userId);
        int remainingForNextMilestone = remainingForNextMilestone(referredFriendCount);
        return new ReferralMeResponse(
                referralCode.getCode(),
                baseRewardTickets,
                milestoneInterval,
                milestoneBonusTickets,
                referredFriendCount,
                referredFriendCount + remainingForNextMilestone,
                remainingForNextMilestone,
                hasEntered,
                !hasEntered
        );
    }

    @Transactional
    public ReferralRedeemResponse redeem(Long userId, String rawCode) {
        String code = normalizeCode(rawCode);

        ReferralRedemption existing = referralRedemptionRepository.findByReferredUserId(userId).orElse(null);
        if (existing != null) {
            if (existing.getReferralCode().equals(code)) {
                User currentUser = lockEligibleUser(userId);
                return response(existing, currentUser.getTickets(), false);
            }
            throw new BusinessException(ErrorCode.REFERRAL_ALREADY_REDEEMED);
        }

        ReferralCode referralCode = referralCodeRepository.findByCode(code)
                .orElseThrow(() -> new BusinessException(ErrorCode.REFERRAL_CODE_INVALID));
        Long referrerUserId = referralCode.getUserId();
        if (referrerUserId.equals(userId)) {
            throw new BusinessException(ErrorCode.REFERRAL_SELF_NOT_ALLOWED);
        }

        User firstLocked = lockEligibleUser(Math.min(userId, referrerUserId));
        User secondLocked = lockEligibleUser(Math.max(userId, referrerUserId));
        User referredUser = firstLocked.getId().equals(userId) ? firstLocked : secondLocked;
        User referrerUser = firstLocked.getId().equals(referrerUserId) ? firstLocked : secondLocked;

        existing = referralRedemptionRepository.findByReferredUserIdForUpdate(userId).orElse(null);
        if (existing != null) {
            if (existing.getReferralCode().equals(code)) {
                return response(existing, referredUser.getTickets(), false);
            }
            throw new BusinessException(ErrorCode.REFERRAL_ALREADY_REDEEMED);
        }

        long pairLow = Math.min(userId, referrerUserId);
        long pairHigh = Math.max(userId, referrerUserId);
        if (referralRedemptionRepository.findByPairForUpdate(pairLow, pairHigh).isPresent()) {
            throw new BusinessException(ErrorCode.REFERRAL_RECIPROCAL_NOT_ALLOWED);
        }

        ReferralRedemption redemption = referralRedemptionRepository.saveAndFlush(
                ReferralRedemption.create(referrerUserId, userId, code, baseRewardTickets)
        );

        long referredFriendCount = referralRedemptionRepository.countByReferrerUserId(referrerUserId);
        boolean milestoneReached = referredFriendCount % milestoneInterval == 0;
        int referrerRewardTickets = baseRewardTickets + (milestoneReached ? milestoneBonusTickets : 0);

        grantReward(referrerUser, redemption.getId(), "referrer", referrerRewardTickets);
        grantReward(referredUser, redemption.getId(), "friend", baseRewardTickets);
        if (milestoneReached) {
            notifyMilestoneReward(referrerUser, referredFriendCount, referrerRewardTickets);
        }
        return response(redemption, referredUser.getTickets(), true);
    }

    private void notifyMilestoneReward(User referrerUser, long referredFriendCount, int grantedTickets) {
        long nextMilestoneAt = referredFriendCount + milestoneInterval;
        String payloadJson = String.format(Locale.ROOT,
                "{\"eventType\":\"REFERRAL_MILESTONE_REWARDED\","
                        + "\"referralCount\":%d,\"baseRewardTickets\":%d,"
                        + "\"bonusRewardTickets\":%d,\"grantedTickets\":%d,"
                        + "\"currentTickets\":%d,\"nextMilestoneAt\":%d,"
                        + "\"showCelebrationPopup\":true}",
                referredFriendCount,
                baseRewardTickets,
                milestoneBonusTickets,
                grantedTickets,
                referrerUser.getTickets(),
                nextMilestoneAt
        );
        notificationService.createAndEnqueue(new NotificationService.CreateCommand(
                referrerUser.getId(),
                NotificationType.REFERRAL_MILESTONE_REWARDED,
                "친구 초대 보너스가 도착했어요!",
                String.format(Locale.ROOT, "추천 친구 %d명을 달성해 추가 티켓 %d장을 받았어요.",
                        referredFriendCount, milestoneBonusTickets),
                "airconnect://mypage/referrals",
                null,
                null,
                payloadJson,
                "referral-milestone:" + referrerUser.getId() + ":" + referredFriendCount
        ));
    }

    private User lockEligibleUser(Long userId) {
        User user = userRepository.findByIdForTicketUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (user.getStatus() != UserStatus.ACTIVE || user.getOnboardingStatus() != OnboardingStatus.FULL) {
            throw new BusinessException(ErrorCode.REFERRAL_ACCOUNT_INELIGIBLE);
        }
        return user;
    }

    private void grantReward(User user, Long redemptionId, String role, int amount) {
        int before = user.getTickets();
        user.addTickets(amount);
        ticketLedgerRepository.save(TicketLedger.grantForReferral(
                user.getId(),
                amount,
                before,
                user.getTickets(),
                redemptionId,
                role
        ));
    }

    private int remainingForNextMilestone(long referredFriendCount) {
        int remainder = (int) (referredFriendCount % milestoneInterval);
        return remainder == 0 ? milestoneInterval : milestoneInterval - remainder;
    }

    private ReferralRedeemResponse response(ReferralRedemption redemption, int myTickets, boolean firstApplied) {
        return new ReferralRedeemResponse(
                redemption.getReferralCode(),
                redemption.getRewardTickets(),
                myTickets,
                firstApplied,
                redemption.getCreatedAt()
        );
    }

    private String normalizeCode(String rawCode) {
        String code = rawCode == null ? "" : rawCode.trim().toUpperCase(Locale.ROOT);
        if (!code.matches("(?=.*[A-Z])(?=.*\\d)[A-Z0-9]{6}")) {
            throw new BusinessException(ErrorCode.REFERRAL_CODE_INVALID);
        }
        return code;
    }

    private String generateUniqueCode() {
        for (int attempt = 0; attempt < CODE_GENERATION_ATTEMPTS; attempt++) {
            StringBuilder code = new StringBuilder(CODE_LENGTH);
            for (int index = 0; index < CODE_LENGTH; index++) {
                code.append(CODE_ALPHABET[RANDOM.nextInt(CODE_ALPHABET.length)]);
            }
            String candidate = code.toString();
            if (candidate.matches("(?=.*[A-Z])(?=.*\\d)[A-Z0-9]{6}")
                    && !referralCodeRepository.existsByCode(candidate)) {
                return candidate;
            }
        }
        throw new BusinessException(ErrorCode.REFERRAL_CODE_GENERATION_FAILED);
    }
}
