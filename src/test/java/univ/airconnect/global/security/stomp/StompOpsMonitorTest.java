package univ.airconnect.global.security.stomp;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.stomp.StompCommand;

import static org.assertj.core.api.Assertions.assertThat;

class StompOpsMonitorTest {

    @Test
    void snapshotCountsAreTracked() {
        StompOpsMonitor monitor = new StompOpsMonitor(1);

        monitor.recordInboundCommand(StompCommand.CONNECT);
        monitor.recordConnectSuccess();

        monitor.recordInboundCommand(StompCommand.SUBSCRIBE);
        monitor.recordSubscribeFailure(new IllegalStateException("subscribe fail"));
        monitor.recordInboundFailure(StompCommand.SUBSCRIBE, new IllegalStateException("inbound fail"));
        monitor.recordSideEffectFailure("CONNECT_SAVE_SESSION", new RuntimeException("redis down"));
        monitor.recordOutboundConnectedFrame();
        monitor.recordOutboundErrorFrame();

        StompOpsSnapshot snapshot = monitor.snapshot();

        assertThat(snapshot.getInboundTotal()).isEqualTo(2);
        assertThat(snapshot.getConnectSuccess()).isEqualTo(1);
        assertThat(snapshot.getSubscribeFailures()).isEqualTo(1);
        assertThat(snapshot.getInboundFailures()).isEqualTo(1);
        assertThat(snapshot.getSideEffectFailures()).isEqualTo(1);
        assertThat(snapshot.getOutboundConnectedFrames()).isEqualTo(1);
        assertThat(snapshot.getOutboundErrorFrames()).isEqualTo(1);
        assertThat(snapshot.getInboundFailureByType())
                .containsKeys("SUBSCRIBE:IllegalStateException", "CONNECT_SAVE_SESSION:RuntimeException");
    }
}

