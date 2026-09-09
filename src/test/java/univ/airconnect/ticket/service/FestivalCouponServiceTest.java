package univ.airconnect.ticket.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import univ.airconnect.global.error.BusinessException;
import univ.airconnect.global.error.ErrorCode;
import univ.airconnect.iap.domain.LedgerRefType;
import univ.airconnect.iap.domain.entity.TicketLedger;
import univ.airconnect.iap.repository.TicketLedgerRepository;
import univ.airconnect.ticket.domain.entity.FestivalCoupon;
import univ.airconnect.ticket.dto.response.FestivalCouponRedeemResponse;
import univ.airconnect.ticket.repository.FestivalCouponRepository;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FestivalCouponServiceTest {

    @Mock
    private FestivalCouponRepository festivalCouponRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private TicketLedgerRepository ticketLedgerRepository;

    private FestivalCouponService service;

    @BeforeEach
    void setUp() {
        service = new FestivalCouponService(
                festivalCouponRepository,
                userRepository,
                ticketLedgerRepository
        );
    }

    @Test
    void redeem_grantsFiveTicketsAndRecordsCouponAndLedger() {
        Long userId = 7L;
        FestivalCoupon coupon = FestivalCoupon.issue("049893");
        User user = User.createEmailUser("festival@example.com", "password-hash");
        int before = user.getTickets();

        when(festivalCouponRepository.findByCodeForUpdate("049893")).thenReturn(Optional.of(coupon));
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));

        FestivalCouponRedeemResponse response = service.redeem(userId, "049893");

        assertThat(response.grantedTickets()).isEqualTo(5);
        assertThat(response.beforeTickets()).isEqualTo(before);
        assertThat(response.afterTickets()).isEqualTo(before + 5);
        assertThat(user.getTickets()).isEqualTo(before + 5);
        assertThat(coupon.isRedeemed()).isTrue();
        assertThat(coupon.getRedeemedByUserId()).isEqualTo(userId);

        ArgumentCaptor<TicketLedger> ledgerCaptor = ArgumentCaptor.forClass(TicketLedger.class);
        verify(ticketLedgerRepository).save(ledgerCaptor.capture());
        TicketLedger ledger = ledgerCaptor.getValue();
        assertThat(ledger.getRefType()).isEqualTo(LedgerRefType.FESTIVAL_COUPON);
        assertThat(ledger.getRefId()).isEqualTo("festival-coupon:049893");
        assertThat(ledger.getChangeAmount()).isEqualTo(5);
        assertThat(ledger.getBeforeAmount()).isEqualTo(before);
        assertThat(ledger.getAfterAmount()).isEqualTo(before + 5);
    }

    @Test
    void redeem_rejectsUnknownCoupon() {
        when(festivalCouponRepository.findByCodeForUpdate("123456")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.redeem(7L, "123456"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.FESTIVAL_COUPON_INVALID);

        verify(userRepository, never()).findByIdForUpdate(7L);
        verify(ticketLedgerRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void redeem_rejectsAlreadyRedeemedCouponWithoutGrantingAgain() {
        FestivalCoupon coupon = FestivalCoupon.issue("311232");
        coupon.redeem(3L, LocalDateTime.now());
        when(festivalCouponRepository.findByCodeForUpdate("311232")).thenReturn(Optional.of(coupon));

        assertThatThrownBy(() -> service.redeem(7L, "311232"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.FESTIVAL_COUPON_ALREADY_REDEEMED);

        verify(userRepository, never()).findByIdForUpdate(7L);
        verify(ticketLedgerRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void redeem_rejectsMalformedCodeBeforeDatabaseLookup() {
        assertThatThrownBy(() -> service.redeem(7L, "12345"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.FESTIVAL_COUPON_INVALID);

        verify(festivalCouponRepository, never()).findByCodeForUpdate(org.mockito.ArgumentMatchers.any());
    }
}
