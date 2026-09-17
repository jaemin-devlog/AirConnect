package univ.airconnect.iap.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IapProductPolicyTest {

    @Test
    void activeAndroidProducts_returnFestivalTicketAmounts() {
        assertThat(IapProductPolicy.fromProductId("com.airconnect.tickets.pack5").getTickets()).isEqualTo(8);
        assertThat(IapProductPolicy.fromProductId("com.airconnect.tickets.pack12").getTickets()).isEqualTo(19);
        assertThat(IapProductPolicy.fromProductId("com.airconnect.tickets.pack30").getTickets()).isEqualTo(50);
        assertThat(IapProductPolicy.fromProductId("com.airconnect.tickets.pack70").getTickets()).isEqualTo(115);
    }

    @Test
    void activeIosProducts_returnFestivalTicketAmounts() {
        assertThat(IapProductPolicy.fromProductId("AirConnect_Economy_5").getTickets()).isEqualTo(8);
        assertThat(IapProductPolicy.fromProductId("AirConnect_PremiumEconomy_10").getTickets()).isEqualTo(19);
        assertThat(IapProductPolicy.fromProductId("AirConnect_Business_30").getTickets()).isEqualTo(50);
        assertThat(IapProductPolicy.fromProductId("AirConnect_FirstClass_50").getTickets()).isEqualTo(115);
    }
}
