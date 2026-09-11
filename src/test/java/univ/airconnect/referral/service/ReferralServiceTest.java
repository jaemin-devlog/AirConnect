package univ.airconnect.referral.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import univ.airconnect.global.error.BusinessException;
import univ.airconnect.global.error.ErrorCode;
import univ.airconnect.iap.domain.LedgerRefType;
import univ.airconnect.iap.domain.entity.TicketLedger;
import univ.airconnect.iap.repository.TicketLedgerRepository;
import univ.airconnect.referral.repository.ReferralCodeRepository;
import univ.airconnect.referral.repository.ReferralRedemptionRepository;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.repository.UserRepository;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = "app.rewards.referral-tickets=5")
@ActiveProfiles("test")
@Import(ReferralService.class)
class ReferralServiceTest {

    @Autowired ReferralService referralService;
    @Autowired ReferralCodeRepository referralCodeRepository;
    @Autowired ReferralRedemptionRepository referralRedemptionRepository;
    @Autowired UserRepository userRepository;
    @Autowired TicketLedgerRepository ticketLedgerRepository;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void getMeIssuesOneStableReferralCode() {
        User user = saveEligibleUser("owner");

        var first = referralService.getMe(user.getId());
        var second = referralService.getMe(user.getId());

        assertThat(first.referralCode()).matches("[A-Z0-9]{6}");
        assertThat(first.referralCode()).containsPattern("[A-Z]");
        assertThat(first.referralCode()).containsPattern("[0-9]");
        assertThat(second.referralCode()).isEqualTo(first.referralCode());
        assertThat(first.rewardTickets()).isEqualTo(5);
        assertThat(first.referredFriendCount()).isZero();
        assertThat(first.hasEnteredReferralCode()).isFalse();
        assertThat(first.canEnterReferralCode()).isTrue();
        assertThat(referralCodeRepository.count()).isEqualTo(1);
    }

    @Test
    void redeemGrantsFiveTicketsToBothUsersAndRecordsTwoLedgers() {
        User referrer = saveEligibleUser("referrer");
        User friend = saveEligibleUser("friend");
        String code = referralService.getMe(referrer.getId()).referralCode();

        var result = referralService.redeem(friend.getId(), code.toLowerCase());

        assertThat(result.rewardTickets()).isEqualTo(5);
        assertThat(result.myTickets()).isEqualTo(15);
        assertThat(result.firstApplied()).isTrue();
        assertThat(userRepository.findById(referrer.getId()).orElseThrow().getTickets()).isEqualTo(15);
        assertThat(userRepository.findById(friend.getId()).orElseThrow().getTickets()).isEqualTo(15);
        assertThat(referralRedemptionRepository.countByReferrerUserId(referrer.getId())).isEqualTo(1);

        List<TicketLedger> ledgers = ticketLedgerRepository.findAll();
        assertThat(ledgers).hasSize(2);
        assertThat(ledgers).extracting(TicketLedger::getRefType)
                .containsOnly(LedgerRefType.REFERRAL_REWARD);
        assertThat(ledgers).extracting(TicketLedger::getUserId)
                .containsExactlyInAnyOrder(referrer.getId(), friend.getId());
        assertThat(ledgers).extracting(TicketLedger::getChangeAmount).containsOnly(5);
        assertThat(ledgers).extracting(TicketLedger::getBeforeAmount).containsOnly(10);
        assertThat(ledgers).extracting(TicketLedger::getAfterAmount).containsOnly(15);
    }

    @Test
    void retryWithSameCodeReturnsPreviousResultWithoutDuplicateReward() {
        User referrer = saveEligibleUser("referrer");
        User friend = saveEligibleUser("friend");
        String code = referralService.getMe(referrer.getId()).referralCode();

        referralService.redeem(friend.getId(), code);
        var retried = referralService.redeem(friend.getId(), code);

        assertThat(retried.firstApplied()).isFalse();
        assertThat(retried.myTickets()).isEqualTo(15);
        assertThat(referralRedemptionRepository.count()).isEqualTo(1);
        assertThat(ticketLedgerRepository.count()).isEqualTo(2);
    }

    @Test
    void userCannotEnterAnotherCodeAfterRedeemingOne() {
        User firstReferrer = saveEligibleUser("first");
        User secondReferrer = saveEligibleUser("second");
        User friend = saveEligibleUser("friend");
        String firstCode = referralService.getMe(firstReferrer.getId()).referralCode();
        String secondCode = referralService.getMe(secondReferrer.getId()).referralCode();
        referralService.redeem(friend.getId(), firstCode);

        assertThatThrownBy(() -> referralService.redeem(friend.getId(), secondCode))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.REFERRAL_ALREADY_REDEEMED);
        assertThat(userRepository.findById(friend.getId()).orElseThrow().getTickets()).isEqualTo(15);
        assertThat(userRepository.findById(secondReferrer.getId()).orElseThrow().getTickets()).isEqualTo(10);
    }

    @Test
    void selfReferralIsRejected() {
        User user = saveEligibleUser("self");
        String code = referralService.getMe(user.getId()).referralCode();

        assertThatThrownBy(() -> referralService.redeem(user.getId(), code))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.REFERRAL_SELF_NOT_ALLOWED);
        assertThat(userRepository.findById(user.getId()).orElseThrow().getTickets()).isEqualTo(10);
    }

    @Test
    void reciprocalReferralBetweenSameTwoUsersIsRejected() {
        User first = saveEligibleUser("first");
        User second = saveEligibleUser("second");
        String firstCode = referralService.getMe(first.getId()).referralCode();
        String secondCode = referralService.getMe(second.getId()).referralCode();
        referralService.redeem(first.getId(), secondCode);

        assertThatThrownBy(() -> referralService.redeem(second.getId(), firstCode))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.REFERRAL_RECIPROCAL_NOT_ALLOWED);
        assertThat(ticketLedgerRepository.count()).isEqualTo(2);
    }

    @Test
    void incompleteAccountCannotUseReferralFeature() {
        User incomplete = userRepository.save(User.createEmailUser(
                UUID.randomUUID() + "@example.com",
                "password-hash"
        ));

        assertThatThrownBy(() -> referralService.getMe(incomplete.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.REFERRAL_ACCOUNT_INELIGIBLE);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void concurrentRetryRewardsEachUserOnlyOnce() throws Exception {
        Long[] ids = new TransactionTemplate(transactionManager).execute(status -> {
            User referrer = saveEligibleUser("concurrent-referrer");
            User friend = saveEligibleUser("concurrent-friend");
            return new Long[]{referrer.getId(), friend.getId()};
        });
        String code = referralService.getMe(ids[0]).referralCode();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> {
                ready.countDown();
                start.await(5, TimeUnit.SECONDS);
                return referralService.redeem(ids[1], code);
            });
            var second = executor.submit(() -> {
                ready.countDown();
                start.await(5, TimeUnit.SECONDS);
                return referralService.redeem(ids[1], code);
            });
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(List.of(first.get(10, TimeUnit.SECONDS).firstApplied(),
                    second.get(10, TimeUnit.SECONDS).firstApplied()))
                    .containsExactlyInAnyOrder(true, false);
        } finally {
            executor.shutdownNow();
        }

        assertThat(userRepository.findById(ids[0]).orElseThrow().getTickets()).isEqualTo(15);
        assertThat(userRepository.findById(ids[1]).orElseThrow().getTickets()).isEqualTo(15);
        assertThat(referralRedemptionRepository.count()).isEqualTo(1);
        assertThat(ticketLedgerRepository.count()).isEqualTo(2);
    }

    private User saveEligibleUser(String nickname) {
        User user = User.createEmailUser(UUID.randomUUID() + "@example.com", "password-hash");
        user.completeSignUp("테스트", nickname, 20260001, "컴퓨터공학과");
        return userRepository.save(user);
    }
}
