package univ.airconnect.iap.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import univ.airconnect.iap.domain.GrantStatus;
import univ.airconnect.iap.domain.IapEnvironment;
import univ.airconnect.iap.domain.IapOrderStatus;
import univ.airconnect.iap.domain.IapStore;
import univ.airconnect.iap.domain.entity.IapOrder;
import univ.airconnect.iap.dto.request.IosTransactionVerifyRequest;
import univ.airconnect.iap.dto.response.IapVerifyResponse;
import univ.airconnect.iap.repository.IapOrderRepository;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IapProcessingServiceRaceTest {

    @Mock private StoreVerifierResolver storeVerifierResolver;
    @Mock private StorePurchaseVerifier storePurchaseVerifier;
    @Mock private IapOrderRepository iapOrderRepository;
    @Mock private TicketGrantService ticketGrantService;
    @Mock private IapRefundService iapRefundService;

    @InjectMocks
    private IapProcessingService iapProcessingService;

    @Test
    @DisplayName("같은 appAccountToken/transactionId를 동시에 2번 검증하면 티켓은 1번만 지급된다")
    void verifyIos_concurrentSamePurchase_oneGrantedOneAlreadyGranted() throws Exception {
        Long userId = 7L;
        String transactionId = "tx-race-1";
        String appAccountToken = "app-account-token-race";

        StoreVerificationResult verificationResult = StoreVerificationResult.builder()
                .store(IapStore.APPLE)
                .productId("com.airconnect.tickets.pack10")
                .transactionId(transactionId)
                .appAccountToken(appAccountToken)
                .environment(IapEnvironment.SANDBOX)
                .verificationHash("hash-1")
                .rawPayloadMasked("masked")
                .valid(true)
                .build();

        AtomicReference<IapOrder> storedOrder = new AtomicReference<>();
        AtomicInteger existingLookups = new AtomicInteger();
        CountDownLatch firstCallCompleted = new CountDownLatch(1);

        when(storeVerifierResolver.resolve(IapStore.APPLE)).thenReturn(storePurchaseVerifier);
        when(storePurchaseVerifier.verify(eq(userId), any())).thenReturn(verificationResult);
        when(iapOrderRepository.findByStoreAndTransactionId(IapStore.APPLE, transactionId)).thenAnswer(invocation -> {
            int call = existingLookups.incrementAndGet();
            if (call > 1) {
                await(firstCallCompleted, "first IAP request completion");
            }
            return Optional.ofNullable(storedOrder.get());
        });
        when(iapOrderRepository.save(any(IapOrder.class))).thenAnswer(invocation -> {
            IapOrder order = invocation.getArgument(0);
            ReflectionTestUtils.setField(order, "id", 100L);
            storedOrder.set(order);
            return order;
        });
        when(iapOrderRepository.findByIdForUpdate(100L)).thenAnswer(invocation -> Optional.ofNullable(storedOrder.get()));
        when(ticketGrantService.grantTickets(any(IapOrder.class), eq(10)))
                .thenAnswer(invocation -> new TicketGrantService.TicketGrantResult(0, 10, "LEDGER-100"));

        IosTransactionVerifyRequest request = new IosTransactionVerifyRequest("signed-jws", transactionId, appAccountToken);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startGate = new CountDownLatch(1);

        try {
            List<Future<IapVerifyResponse>> futures = List.of(
                    executor.submit(task(startGate, () -> iapProcessingService.verifyIos(userId, request), firstCallCompleted)),
                    executor.submit(task(startGate, () -> iapProcessingService.verifyIos(userId, request), firstCallCompleted))
            );

            startGate.countDown();

            int grantedCount = 0;
            int alreadyGrantedCount = 0;
            for (Future<IapVerifyResponse> future : futures) {
                IapVerifyResponse response = future.get(5, TimeUnit.SECONDS);
                if (response.getGrantStatus() == GrantStatus.GRANTED) {
                    grantedCount++;
                } else if (response.getGrantStatus() == GrantStatus.ALREADY_GRANTED) {
                    alreadyGrantedCount++;
                }
            }

            assertThat(grantedCount).isEqualTo(1);
            assertThat(alreadyGrantedCount).isEqualTo(1);
            assertThat(storedOrder.get().getStatus()).isEqualTo(IapOrderStatus.GRANTED);
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private <T> Callable<T> task(CountDownLatch startGate, Callable<T> delegate, CountDownLatch completionGate) {
        return () -> {
            await(startGate, "IAP start gate");
            try {
                return delegate.call();
            } finally {
                completionGate.countDown();
            }
        };
    }

    private void await(CountDownLatch latch, String label) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timeout waiting for " + label);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for " + label, e);
        }
    }
}
