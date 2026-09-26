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
        assertThat(IapProductPolicy.fromProductId("AirConnect_Economy_5").getCatalogPriceKrw()).isEqualTo(1_100);
        assertThat(IapProductPolicy.fromProductId("AirConnect_PremiumEconomy_10").getCatalogPriceKrw()).isEqualTo(2_200);
        assertThat(IapProductPolicy.fromProductId("AirConnect_Business_30").getCatalogPriceKrw()).isEqualTo(5_500);
        assertThat(IapProductPolicy.fromProductId("AirConnect_FirstClass_50").getCatalogPriceKrw()).isEqualTo(11_000);
    }
}
