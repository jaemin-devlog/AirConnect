package univ.airconnect.iap.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import univ.airconnect.iap.domain.GrantStatus;
import univ.airconnect.iap.domain.IapEnvironment;
import univ.airconnect.iap.domain.IapStore;
import univ.airconnect.iap.domain.entity.IapOrder;
import univ.airconnect.iap.dto.request.IosTransactionVerifyRequest;
import univ.airconnect.iap.dto.request.IosTransactionsSyncRequest;
import univ.airconnect.iap.dto.response.IapSyncResponse;
import univ.airconnect.iap.dto.response.IapVerifyResponse;
import univ.airconnect.iap.exception.IapErrorCode;
import univ.airconnect.iap.exception.IapException;
import univ.airconnect.iap.repository.IapOrderRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IapProcessingServiceTest {

    @Mock
    private StoreVerifierResolver storeVerifierResolver;
    @Mock
    private StorePurchaseVerifier storePurchaseVerifier;
    @Mock
    private IapOrderRepository iapOrderRepository;
    @Mock
    private TicketGrantService ticketGrantService;
    @Mock
    private IapRefundService iapRefundService;

    @InjectMocks
    private IapProcessingService iapProcessingService;

    @Test
    void verifyIos_grantsTickets_whenFirstProcessed() {
        Long userId = 1L;
        IosTransactionVerifyRequest request = new IosTransactionVerifyRequest();
        ReflectionTestUtils.setField(request, "signedTransactionInfo", "jws");

        StoreVerificationResult verificationResult = StoreVerificationResult.builder()
                .store(IapStore.APPLE)
                .productId("com.airconnect.tickets.pack10")
                .transactionId("tx-1")
                .environment(IapEnvironment.SANDBOX)
                .verificationHash("hash")
                .rawPayloadMasked("mask")
                .valid(true)
                .build();

        IapOrder order = IapOrder.createPending(userId, IapStore.APPLE, "com.airconnect.tickets.pack10",
                "tx-1", null, null, null, null, IapEnvironment.SANDBOX, "hash", "mask");
        ReflectionTestUtils.setField(order, "id", 10L);

        when(storeVerifierResolver.resolve(IapStore.APPLE)).thenReturn(storePurchaseVerifier);
        when(storePurchaseVerifier.verify(eq(userId), any())).thenReturn(verificationResult);
        when(iapOrderRepository.findByStoreAndTransactionId(IapStore.APPLE, "tx-1")).thenReturn(Optional.empty());
        when(iapOrderRepository.save(any(IapOrder.class))).thenReturn(order);
        when(iapOrderRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(order));
        when(ticketGrantService.grantTickets(any(IapOrder.class), eq(10)))
                .thenReturn(new TicketGrantService.TicketGrantResult(17, 27, "TICKET_LEDGER_1"));

        IapVerifyResponse response = iapProcessingService.verifyIos(userId, request);

        assertThat(response.getGrantStatus()).isEqualTo(GrantStatus.GRANTED);
        assertThat(response.getGrantedTickets()).isEqualTo(10);
        assertThat(response.getBeforeTickets()).isEqualTo(17);
        assertThat(response.getAfterTickets()).isEqualTo(27);
    }

    @Test
    void verifyIos_returnsAlreadyGranted_whenExistingGranted() {
        Long userId = 1L;
        IosTransactionVerifyRequest request = new IosTransactionVerifyRequest();
        ReflectionTestUtils.setField(request, "signedTransactionInfo", "jws");

        StoreVerificationResult verificationResult = StoreVerificationResult.builder()
                .store(IapStore.APPLE)
                .productId("com.airconnect.tickets.pack5")
                .transactionId("tx-dup")
                .environment(IapEnvironment.SANDBOX)
                .verificationHash("hash")
                .rawPayloadMasked("mask")
                .valid(true)
                .build();

        IapOrder existing = IapOrder.createPending(userId, IapStore.APPLE, "com.airconnect.tickets.pack5",
                "tx-dup", null, null, null, null, IapEnvironment.SANDBOX, "hash", "mask");
        ReflectionTestUtils.setField(existing, "id", 33L);
        existing.markVerified();
        existing.markGranted(5, 20, 25);

        when(storeVerifierResolver.resolve(IapStore.APPLE)).thenReturn(storePurchaseVerifier);
        when(storePurchaseVerifier.verify(eq(userId), any())).thenReturn(verificationResult);
        when(iapOrderRepository.findByStoreAndTransactionId(IapStore.APPLE, "tx-dup")).thenReturn(Optional.of(existing));
        when(iapOrderRepository.findByIdForUpdate(33L)).thenReturn(Optional.of(existing));

        IapVerifyResponse response = iapProcessingService.verifyIos(userId, request);

        assertThat(response.getGrantStatus()).isEqualTo(GrantStatus.ALREADY_GRANTED);
        assertThat(response.getGrantedTickets()).isEqualTo(5);
        assertThat(response.getAfterTickets()).isEqualTo(25);
    }

    @Test
    void verifyIos_refundsAlreadyGrantedOrder_whenRevocationArrivesLater() {
        Long userId = 1L;
        IosTransactionVerifyRequest request = new IosTransactionVerifyRequest();
        ReflectionTestUtils.setField(request, "signedTransactionInfo", "jws");

        StoreVerificationResult verificationResult = StoreVerificationResult.builder()
                .store(IapStore.APPLE)
                .productId("AirConnect_PremiumEconomy_10")
                .transactionId("tx-refund")
                .environment(IapEnvironment.SANDBOX)
                .verificationHash("hash")
                .rawPayloadMasked("mask")
                .valid(false)
                .transactionRevoked(true)
                .build();

        IapOrder existing = IapOrder.createPending(userId, IapStore.APPLE, "AirConnect_PremiumEconomy_10",
                "tx-refund", null, null, null, null, IapEnvironment.SANDBOX, "hash", "mask");
        ReflectionTestUtils.setField(existing, "id", 88L);
        existing.markVerified();
        existing.markGranted(12, 7, 19);

        when(storeVerifierResolver.resolve(IapStore.APPLE)).thenReturn(storePurchaseVerifier);
        when(storePurchaseVerifier.verify(eq(userId), any())).thenReturn(verificationResult);
        when(iapOrderRepository.findByStoreAndTransactionId(IapStore.APPLE, "tx-refund")).thenReturn(Optional.of(existing));
        when(iapOrderRepository.findByIdForUpdate(88L)).thenReturn(Optional.of(existing));

        IapVerifyResponse response = iapProcessingService.verifyIos(userId, request);

        assertThat(response.getGrantStatus()).isEqualTo(GrantStatus.REJECTED);
        verify(iapRefundService).refundGrantedOrder(existing, "verify:APPLE");
    }

    @Test
    void syncIos_returnsPartialSuccess() {
        Long userId = 1L;

        IosTransactionsSyncRequest req = new IosTransactionsSyncRequest();
        IosTransactionsSyncRequest.IosSyncItem okItem = new IosTransactionsSyncRequest.IosSyncItem();
        ReflectionTestUtils.setField(okItem, "signedTransactionInfo", "jws-ok");
        ReflectionTestUtils.setField(okItem, "transactionId", "tx-ok");

        IosTransactionsSyncRequest.IosSyncItem failItem = new IosTransactionsSyncRequest.IosSyncItem();
        ReflectionTestUtils.setField(failItem, "signedTransactionInfo", "jws-fail");
        ReflectionTestUtils.setField(failItem, "transactionId", "tx-fail");

        ReflectionTestUtils.setField(req, "transactions", List.of(okItem, failItem));

        when(storeVerifierResolver.resolve(IapStore.APPLE)).thenReturn(storePurchaseVerifier);
        when(storePurchaseVerifier.verify(eq(userId), any()))
                .thenReturn(StoreVerificationResult.builder()
                        .store(IapStore.APPLE)
                        .productId("com.airconnect.tickets.pack5")
                        .transactionId("tx-ok")
                        .environment(IapEnvironment.SANDBOX)
                        .verificationHash("h1")
                        .rawPayloadMasked("m1")
                        .valid(true)
                        .build())
                .thenThrow(new IapException(IapErrorCode.IAP_INVALID_TRANSACTION));

        IapOrder order = IapOrder.createPending(userId, IapStore.APPLE, "com.airconnect.tickets.pack5",
                "tx-ok", null, null, null, null, IapEnvironment.SANDBOX, "h1", "m1");
        ReflectionTestUtils.setField(order, "id", 99L);

        when(iapOrderRepository.findByStoreAndTransactionId(IapStore.APPLE, "tx-ok")).thenReturn(Optional.empty());
        when(iapOrderRepository.save(any(IapOrder.class))).thenReturn(order);
        when(iapOrderRepository.findByIdForUpdate(99L)).thenReturn(Optional.of(order));
        when(ticketGrantService.grantTickets(any(IapOrder.class), eq(5)))
                .thenReturn(new TicketGrantService.TicketGrantResult(10, 15, "TICKET_LEDGER_99"));

        IapSyncResponse response = iapProcessingService.syncIos(userId, req);

        assertThat(response.getTotal()).isEqualTo(2);
        assertThat(response.getSuccessCount()).isEqualTo(1);
        assertThat(response.getFailureCount()).isEqualTo(1);
    }

    @Test
    void verifyIos_throwsForbidden_whenExistingTransactionOwnedByAnotherUser() {
        Long requesterUserId = 1L;
        Long ownerUserId = 99L;
        IosTransactionVerifyRequest request = new IosTransactionVerifyRequest();
        ReflectionTestUtils.setField(request, "signedTransactionInfo", "jws");

        StoreVerificationResult verificationResult = StoreVerificationResult.builder()
                .store(IapStore.APPLE)
                .productId("com.airconnect.tickets.pack10")
                .transactionId("tx-foreign")
                .appAccountToken("app-token")
                .environment(IapEnvironment.SANDBOX)
                .verificationHash("hash")
                .rawPayloadMasked("mask")
                .valid(true)
                .build();

        IapOrder foreignOrder = IapOrder.createPending(ownerUserId, IapStore.APPLE, "com.airconnect.tickets.pack10",
                "tx-foreign", null, null, null, "app-token", IapEnvironment.SANDBOX, "hash", "mask");
        ReflectionTestUtils.setField(foreignOrder, "id", 44L);

        when(storeVerifierResolver.resolve(IapStore.APPLE)).thenReturn(storePurchaseVerifier);
        when(storePurchaseVerifier.verify(eq(requesterUserId), any())).thenReturn(verificationResult);
        when(iapOrderRepository.findByStoreAndTransactionId(IapStore.APPLE, "tx-foreign"))
                .thenReturn(Optional.of(foreignOrder));
        when(iapOrderRepository.findByIdForUpdate(44L)).thenReturn(Optional.of(foreignOrder));

        assertThatThrownBy(() -> iapProcessingService.verifyIos(requesterUserId, request))
                .isInstanceOf(IapException.class)
                .extracting("errorCode")
                .isEqualTo(IapErrorCode.IAP_FORBIDDEN);
    }

    @Test
    void verifyIos_marksRevokedAndDoesNotGrant_whenTransactionRevoked() {
        Long userId = 1L;
        IosTransactionVerifyRequest request = new IosTransactionVerifyRequest();
        ReflectionTestUtils.setField(request, "signedTransactionInfo", "jws");

        StoreVerificationResult verificationResult = StoreVerificationResult.builder()
                .store(IapStore.APPLE)
                .productId("com.airconnect.tickets.pack10")
                .transactionId("tx-revoked")
                .appAccountToken("app-token")
                .environment(IapEnvironment.SANDBOX)
                .verificationHash("hash")
                .rawPayloadMasked("mask")
                .valid(false)
                .transactionRevoked(true)
                .build();

        IapOrder order = IapOrder.createPending(userId, IapStore.APPLE, "com.airconnect.tickets.pack10",
                "tx-revoked", null, null, null, "app-token", IapEnvironment.SANDBOX, "hash", "mask");
        ReflectionTestUtils.setField(order, "id", 77L);

        when(storeVerifierResolver.resolve(IapStore.APPLE)).thenReturn(storePurchaseVerifier);
        when(storePurchaseVerifier.verify(eq(userId), any())).thenReturn(verificationResult);
        when(iapOrderRepository.findByStoreAndTransactionId(IapStore.APPLE, "tx-revoked")).thenReturn(Optional.empty());
        when(iapOrderRepository.save(any(IapOrder.class))).thenReturn(order);
        when(iapOrderRepository.findByIdForUpdate(77L)).thenReturn(Optional.of(order));

        IapVerifyResponse response = iapProcessingService.verifyIos(userId, request);

        assertThat(response.getGrantStatus()).isEqualTo(GrantStatus.REJECTED);
        assertThat(order.getStatus()).isEqualTo(univ.airconnect.iap.domain.IapOrderStatus.REVOKED);
        verify(ticketGrantService, never()).grantTickets(any(IapOrder.class), eq(10));
    }

    @Test
    void verifyIos_isIdempotent_whenSameTransactionRequestedTwice() {
        Long userId = 1L;
        IosTransactionVerifyRequest request = new IosTransactionVerifyRequest();
        ReflectionTestUtils.setField(request, "signedTransactionInfo", "jws");

        StoreVerificationResult verificationResult = StoreVerificationResult.builder()
                .store(IapStore.APPLE)
                .productId("com.airconnect.tickets.pack5")
                .transactionId("tx-repeat")
                .appAccountToken("app-token")
                .environment(IapEnvironment.SANDBOX)
                .verificationHash("hash")
                .rawPayloadMasked("mask")
                .valid(true)
                .build();

        IapOrder order = IapOrder.createPending(userId, IapStore.APPLE, "com.airconnect.tickets.pack5",
                "tx-repeat", null, null, null, "app-token", IapEnvironment.SANDBOX, "hash", "mask");
        ReflectionTestUtils.setField(order, "id", 55L);

        when(storeVerifierResolver.resolve(IapStore.APPLE)).thenReturn(storePurchaseVerifier);
        when(storePurchaseVerifier.verify(eq(userId), any())).thenReturn(verificationResult);
        when(iapOrderRepository.save(any(IapOrder.class))).thenReturn(order);
        when(iapOrderRepository.findByStoreAndTransactionId(IapStore.APPLE, "tx-repeat"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(order));
        when(iapOrderRepository.findByIdForUpdate(55L)).thenReturn(Optional.of(order));
        when(ticketGrantService.grantTickets(any(IapOrder.class), eq(5)))
                .thenReturn(new TicketGrantService.TicketGrantResult(10, 15, "TICKET_LEDGER_55"));

        IapVerifyResponse first = iapProcessingService.verifyIos(userId, request);
        IapVerifyResponse second = iapProcessingService.verifyIos(userId, request);

        assertThat(first.getGrantStatus()).isEqualTo(GrantStatus.GRANTED);
        assertThat(second.getGrantStatus()).isEqualTo(GrantStatus.ALREADY_GRANTED);
        verify(ticketGrantService, times(1)).grantTickets(any(IapOrder.class), eq(5));
    }
}
