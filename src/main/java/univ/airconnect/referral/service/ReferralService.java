package univ.airconnect.referral.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import univ.airconnect.global.error.BusinessException;
import univ.airconnect.global.error.ErrorCode;
import univ.airconnect.iap.domain.entity.TicketLedger;
import univ.airconnect.iap.repository.TicketLedgerRepository;
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

    @Value("${app.rewards.referral-tickets:5}")
    private int rewardTickets;

    @Transactional
    public ReferralMeResponse getMe(Long userId) {
        User user = lockEligibleUser(userId);
        ReferralCode referralCode = referralCodeRepository.findByUserId(userId)
                .orElseGet(() -> referralCodeRepository.save(ReferralCode.issue(user.getId(), generateUniqueCode())));
        boolean hasEntered = referralRedemptionRepository.findByReferredUserId(userId).isPresent();

        return new ReferralMeResponse(
                referralCode.getCode(),
                rewardTickets,
                referralRedemptionRepository.countByReferrerUserId(userId),
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
                ReferralRedemption.create(referrerUserId, userId, code, rewardTickets)
        );

        grantReward(referrerUser, redemption.getId(), "referrer");
        grantReward(referredUser, redemption.getId(), "friend");
        return response(redemption, referredUser.getTickets(), true);
    }

    private User lockEligibleUser(Long userId) {
        User user = userRepository.findByIdForTicketUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (user.getStatus() != UserStatus.ACTIVE || user.getOnboardingStatus() != OnboardingStatus.FULL) {
            throw new BusinessException(ErrorCode.REFERRAL_ACCOUNT_INELIGIBLE);
        }
        return user;
    }

    private void grantReward(User user, Long redemptionId, String role) {
        int before = user.getTickets();
        user.addTickets(rewardTickets);
        ticketLedgerRepository.save(TicketLedger.grantForReferral(
                user.getId(),
                rewardTickets,
                before,
                user.getTickets(),
                redemptionId,
                role
        ));
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
