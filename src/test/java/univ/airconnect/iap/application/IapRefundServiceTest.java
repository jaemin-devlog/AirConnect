package univ.airconnect.iap.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import univ.airconnect.auth.domain.entity.SocialProvider;
import univ.airconnect.iap.domain.IapEnvironment;
import univ.airconnect.iap.domain.IapStore;
import univ.airconnect.iap.domain.LedgerRefType;
import univ.airconnect.iap.domain.entity.IapOrder;
import univ.airconnect.iap.domain.entity.TicketLedger;
import univ.airconnect.iap.repository.IapOrderRepository;
import univ.airconnect.iap.repository.TicketLedgerRepository;
import univ.airconnect.user.domain.OnboardingStatus;
import univ.airconnect.user.domain.UserRole;
import univ.airconnect.user.domain.UserStatus;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IapRefundServiceTest {

    @Mock
    private IapOrderRepository iapOrderRepository;
    @Mock
    private TicketLedgerRepository ticketLedgerRepository;
    @Mock
    private UserRepository userRepository;

    @Test
    void refundAppleTransaction_reversesTicketsEvenWhenBalanceWouldGoNegative() {
        IapRefundService refundService = new IapRefundService(iapOrderRepository, ticketLedgerRepository, userRepository);

        IapOrder order = IapOrder.createPending(
                1L,
                IapStore.APPLE,
                "AirConnect_FirstClass_50",
                "tx-1",
                null,
                null,
                null,
                "app-token",
                IapEnvironment.SANDBOX,
                "hash",
                "mask"
        );
        ReflectionTestUtils.setField(order, "id", 10L);
        order.markVerified();
        order.markGranted(70, 5, 75);

        User user = user(1L, 3);
        when(iapOrderRepository.findByStoreAndTransactionId(IapStore.APPLE, "tx-1")).thenReturn(Optional.of(order));
        when(iapOrderRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(order));
        when(ticketLedgerRepository.findByRefTypeAndRefId(LedgerRefType.IAP_REFUND, "10")).thenReturn(Optional.empty());
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(ticketLedgerRepository.save(any(TicketLedger.class))).thenAnswer(invocation -> {
            TicketLedger ledger = invocation.getArgument(0);
            ReflectionTestUtils.setField(ledger, "id", 99L);
            return ledger;
        });

        IapRefundService.RefundResult result = refundService.refundAppleTransaction("tx-1", "apple_webhook:REFUND");

        ArgumentCaptor<TicketLedger> captor = ArgumentCaptor.forClass(TicketLedger.class);
        verify(ticketLedgerRepository).save(captor.capture());
        assertThat(result.refunded()).isTrue();
        assertThat(user.getTickets()).isEqualTo(-67);
        assertThat(order.getStatus().name()).isEqualTo("REFUNDED");
        assertThat(captor.getValue().getRefType()).isEqualTo(LedgerRefType.IAP_REFUND);
        assertThat(captor.getValue().getChangeAmount()).isEqualTo(-70);
        assertThat(captor.getValue().getAfterAmount()).isEqualTo(-67);
    }

    private User user(Long id, int tickets) {
        User user = User.builder()
                .provider(SocialProvider.APPLE)
                .socialId("social-" + id)
                .email("user" + id + "@airconnect.test")
                .status(UserStatus.ACTIVE)
                .role(UserRole.USER)
                .onboardingStatus(OnboardingStatus.FULL)
                .createdAt(LocalDateTime.now())
                .tickets(tickets)
                .build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
