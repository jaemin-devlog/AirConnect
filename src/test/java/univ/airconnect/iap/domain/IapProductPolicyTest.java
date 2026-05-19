package univ.airconnect.iap.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IapProductPolicyTest {

    @Test
    void fromProductId_returnsPolicy_forAndroidPack70() {
        IapProductPolicy policy = IapProductPolicy.fromProductId("com.airconnect.tickets.pack70");

        assertThat(policy).isEqualTo(IapProductPolicy.LEGACY_PACK_70);
        assertThat(policy.getTickets()).isEqualTo(70);
    }
}
