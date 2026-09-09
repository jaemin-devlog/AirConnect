package univ.airconnect.global.transaction;

import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * DB 상태를 외부에 알리는 작업을 현재 트랜잭션이 커밋된 뒤 실행한다.
 * 트랜잭션이 없는 호출은 기존 동작과의 호환을 위해 즉시 실행한다.
 */
@Slf4j
public final class AfterCommitExecutor {

    private AfterCommitExecutor() {
    }

    public static void execute(String operation, Runnable action) {
        Runnable safeAction = () -> {
            try {
                action.run();
            } catch (RuntimeException exception) {
                // DB commit은 이미 끝났으므로 전송 장애가 저장 결과를 되돌려서는 안 된다.
                log.error("After-commit action failed. operation={}", operation, exception);
            }
        };

        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    safeAction.run();
                }
            });
            return;
        }

        safeAction.run();
    }
}
