package univ.airconnect.global.security.stomp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
public class StompOpsMonitor {

    private final long inboundFailureWarnThreshold;

    private final AtomicLong inboundTotal = new AtomicLong();
    private final AtomicLong inboundFailures = new AtomicLong();
    private final AtomicLong connectSuccess = new AtomicLong();
    private final AtomicLong connectFailures = new AtomicLong();
    private final AtomicLong subscribeSuccess = new AtomicLong();
    private final AtomicLong subscribeFailures = new AtomicLong();
    private final AtomicLong sideEffectFailures = new AtomicLong();
    private final AtomicLong outboundConnectedFrames = new AtomicLong();
    private final AtomicLong outboundErrorFramesCreated = new AtomicLong();
    private final AtomicLong outboundErrorFrames = new AtomicLong();

    private final AtomicLong lastInboundFailuresReported = new AtomicLong();

    private final Map<String, AtomicLong> inboundFailureByType = new ConcurrentHashMap<>();

    public StompOpsMonitor(@Value("${app.websocket.ops.inbound-failure-warn-threshold:20}") long inboundFailureWarnThreshold) {
        this.inboundFailureWarnThreshold = inboundFailureWarnThreshold;
    }

    public void recordInboundCommand(StompCommand command) {
        if (command != null) {
            inboundTotal.incrementAndGet();
        }
    }

    public void recordInboundFailure(StompCommand command, Throwable ex) {
        inboundFailures.incrementAndGet();
        String commandPart = command != null ? command.name() : "UNKNOWN";
        String typePart = ex != null ? ex.getClass().getSimpleName() : "UnknownException";
        String key = commandPart + ":" + typePart;
        inboundFailureByType.computeIfAbsent(key, ignored -> new AtomicLong()).incrementAndGet();
    }

    public void recordConnectSuccess() {
        connectSuccess.incrementAndGet();
    }

    public void recordConnectFailure(Throwable ex) {
        connectFailures.incrementAndGet();
        inboundFailureByType.computeIfAbsent("CONNECT:" + safeType(ex), ignored -> new AtomicLong()).incrementAndGet();
    }

    public void recordSubscribeSuccess() {
        subscribeSuccess.incrementAndGet();
    }

    public void recordSubscribeFailure(Throwable ex) {
        subscribeFailures.incrementAndGet();
        inboundFailureByType.computeIfAbsent("SUBSCRIBE:" + safeType(ex), ignored -> new AtomicLong()).incrementAndGet();
    }

    public void recordSideEffectFailure(String stage, Throwable ex) {
        sideEffectFailures.incrementAndGet();
        inboundFailureByType
                .computeIfAbsent(stage + ":" + safeType(ex), ignored -> new AtomicLong())
                .incrementAndGet();
    }

    public void recordOutboundConnectedFrame() {
        outboundConnectedFrames.incrementAndGet();
    }

    public void recordOutboundErrorFrameCreated() {
        outboundErrorFramesCreated.incrementAndGet();
    }

    public void recordOutboundErrorFrame() {
        outboundErrorFrames.incrementAndGet();
    }

    public StompOpsSnapshot snapshot() {
        return StompOpsSnapshot.builder()
                .capturedAt(OffsetDateTime.now())
                .inboundTotal(inboundTotal.get())
                .inboundFailures(inboundFailures.get())
                .connectSuccess(connectSuccess.get())
                .connectFailures(connectFailures.get())
                .subscribeSuccess(subscribeSuccess.get())
                .subscribeFailures(subscribeFailures.get())
                .sideEffectFailures(sideEffectFailures.get())
                .outboundConnectedFrames(outboundConnectedFrames.get())
                .outboundErrorFramesCreated(outboundErrorFramesCreated.get())
                .outboundErrorFrames(outboundErrorFrames.get())
                .inboundFailureByType(snapshotFailureByType())
                .build();
    }

    @Scheduled(fixedDelayString = "${app.websocket.ops.report-interval-ms:60000}")
    public void reportPeriodicStats() {
        long total = inboundTotal.get();
        long failures = inboundFailures.get();
        long deltaFailures = failures - lastInboundFailuresReported.getAndSet(failures);

        log.info("STOMP OPS STATS: inboundTotal={}, inboundFailures={}, connectSuccess={}, connectFailures={}, subscribeSuccess={}, subscribeFailures={}, sideEffectFailures={}, outboundConnectedFrames={}, outboundErrorFramesCreated={}, outboundErrorFrames={}",
                total,
                failures,
                connectSuccess.get(),
                connectFailures.get(),
                subscribeSuccess.get(),
                subscribeFailures.get(),
                sideEffectFailures.get(),
                outboundConnectedFrames.get(),
                outboundErrorFramesCreated.get(),
                outboundErrorFrames.get());

        if (deltaFailures >= inboundFailureWarnThreshold) {
            log.warn("STOMP OPS ALERT: inbound failures in interval exceeded threshold. deltaFailures={}, threshold={}, topFailureKeys={}",
                    deltaFailures,
                    inboundFailureWarnThreshold,
                    snapshotFailureByType());
        }
    }

    private Map<String, Long> snapshotFailureByType() {
        Map<String, Long> result = new HashMap<>();
        inboundFailureByType.forEach((key, value) -> result.put(key, value.get()));
        return result;
    }

    private String safeType(Throwable ex) {
        return ex != null ? ex.getClass().getSimpleName() : "UnknownException";
    }
}


