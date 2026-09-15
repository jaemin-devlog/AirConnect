package univ.airconnect.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import univ.airconnect.auth.domain.entity.SocialProvider;
import univ.airconnect.global.error.BusinessException;
import univ.airconnect.iap.domain.entity.TicketLedger;
import univ.airconnect.iap.repository.TicketLedgerRepository;
import univ.airconnect.notification.service.NotificationService;
import univ.airconnect.user.domain.OnboardingStatus;
import univ.airconnect.user.domain.UserRole;
import univ.airconnect.user.domain.UserStatus;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.repository.UserRepository;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminBulkTicketGrantServiceTest {
    @Mock AdminBulkTicketGrantRepository operations;
    @Mock UserRepository users;
    @Mock TicketLedgerRepository ledgers;
    @Mock NotificationService notifications;
    @Mock AdminAuditLogService audits;
    @Mock PlatformTransactionManager transactions;

    private AdminBulkTicketGrantService service;

    @BeforeEach
    void setUp() {
        lenient().when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        service = new AdminBulkTicketGrantService(operations, users, ledgers, notifications,
                audits, new ObjectMapper(), transactions);
        lenient().when(users.findById(99L)).thenReturn(Optional.of(user(99L, UserRole.ADMIN, 0)));
    }

    @Test
    void previewCountsOnlyActiveRegularUsers() {
        when(users.countByStatusAndRole(UserStatus.ACTIVE, UserRole.USER)).thenReturn(12L);
        AdminDtos.BulkTicketGrantPreview result = service.preview(99L);
        assertThat(result.targetCount()).isEqualTo(12);
        assertThat(result.targetDescription()).contains("관리자", "탈퇴", "정지");
    }

    @Test
    void grantChangesEveryEligibleBalanceAndCreatesHistoryAndNotification() {
        User first = user(1L, UserRole.USER, 3);
        User second = user(2L, UserRole.USER, 10);
        when(users.findActiveRegularUsersForBulkTicketUpdate()).thenReturn(List.of(first, second));
        when(operations.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        String operationId = UUID.randomUUID().toString();

        AdminDtos.BulkTicketGrantResult result = service.grant(99L,
                new AdminRequests.BulkTicketGrantRequest(operationId, 5, "이벤트 보상 티켓입니다."));

        assertThat(first.getTickets()).isEqualTo(8);
        assertThat(second.getTickets()).isEqualTo(15);
        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.targetCount()).isEqualTo(2);
        assertThat(result.totalGrantedTickets()).isEqualTo(10);
        ArgumentCaptor<TicketLedger> histories = ArgumentCaptor.forClass(TicketLedger.class);
        verify(ledgers, times(2)).save(histories.capture());
        assertThat(histories.getAllValues()).allSatisfy(history -> {
            assertThat(history.getChangeAmount()).isEqualTo(5);
            assertThat(history.getRefId()).contains(operationId);
        });
        ArgumentCaptor<NotificationService.CreateCommand> messages =
                ArgumentCaptor.forClass(NotificationService.CreateCommand.class);
        verify(notifications, times(2)).createAndEnqueue(messages.capture());
        assertThat(messages.getAllValues()).allSatisfy(message -> {
            assertThat(message.body()).isEqualTo("이벤트 보상 티켓입니다.");
            assertThat(message.dedupeKey()).contains(operationId);
        });
        verify(audits).recordBulkTicketGrant(99L, operationId, 5, 2, 10L, "이벤트 보상 티켓입니다.");
    }

    @Test
    void duplicateOperationReturnsCommittedResultWithoutPayingAgain() throws Exception {
        String operationId = UUID.randomUUID().toString();
        AdminRequests.BulkTicketGrantRequest request =
                new AdminRequests.BulkTicketGrantRequest(operationId, 3, "감사 티켓입니다.");
        Method hash = AdminBulkTicketGrantService.class.getDeclaredMethod(
                "requestHash", AdminRequests.BulkTicketGrantRequest.class);
        hash.setAccessible(true);
        AdminBulkTicketGrant completed = AdminBulkTicketGrant.claim(
                operationId, (String) hash.invoke(service, request), 99L, 3, request.message(), LocalDateTime.now());
        completed.complete(4, 4, 12, LocalDateTime.now());
        when(operations.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate"));
        when(operations.findByOperationId(operationId)).thenReturn(Optional.of(completed));

        AdminDtos.BulkTicketGrantResult result = service.grant(99L, request);

        assertThat(result.totalGrantedTickets()).isEqualTo(12);
        verify(users, never()).findActiveRegularUsersForBulkTicketUpdate();
        verifyNoInteractions(ledgers, notifications, audits);
    }

    @Test
    void invalidAmountAndMessageAreRejectedBeforeWriting() {
        String operationId = UUID.randomUUID().toString();
        assertThatThrownBy(() -> service.grant(99L,
                new AdminRequests.BulkTicketGrantRequest(operationId, 0, "안내")))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.grant(99L,
                new AdminRequests.BulkTicketGrantRequest(operationId, 1, " ")))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(operations, ledgers, notifications, audits);
    }

    private User user(Long id, UserRole role, int tickets) {
        User user = User.builder()
                .provider(SocialProvider.KAKAO)
                .socialId("social-" + id)
                .role(role)
                .status(UserStatus.ACTIVE)
                .onboardingStatus(OnboardingStatus.FULL)
                .tickets(tickets)
                .createdAt(LocalDateTime.now())
                .build();
        org.springframework.test.util.ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
