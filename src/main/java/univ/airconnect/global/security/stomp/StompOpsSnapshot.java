package univ.airconnect.global.security.stomp;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.Map;

@Getter
@Builder
public class StompOpsSnapshot {

    private final OffsetDateTime capturedAt;
    private final long inboundTotal;
    private final long inboundFailures;
    private final long connectSuccess;
    private final long connectFailures;
    private final long subscribeSuccess;
    private final long subscribeFailures;
    private final long sideEffectFailures;
    private final long outboundConnectedFrames;
    private final long outboundErrorFramesCreated;
    private final long outboundErrorFrames;
    private final Map<String, Long> inboundFailureByType;
}


