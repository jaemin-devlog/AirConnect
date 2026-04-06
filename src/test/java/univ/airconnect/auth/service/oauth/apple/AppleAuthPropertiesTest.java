package univ.airconnect.auth.service.oauth.apple;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AppleAuthPropertiesTest {

    @Test
    void resolveAllowedAudiences_returnsConfiguredLoginAudiencesFirst() {
        AppleAuthProperties properties = new AppleAuthProperties();
        properties.setBundleId("com.airconnect.app");
        properties.setServiceId("com.airconnect.web");
        properties.getLogin().setAllowedAudiences(List.of("com.custom.first", "com.custom.second"));

        List<String> audiences = properties.resolveAllowedAudiences();

        assertThat(audiences).containsExactly("com.custom.first", "com.custom.second");
    }

    @Test
    void resolveAllowedAudiences_fallsBackToBundleServiceAndClient() {
        AppleAuthProperties properties = new AppleAuthProperties();
        properties.setBundleId("com.airconnect.app");
        properties.setServiceId("com.airconnect.web");
        properties.setClientId("com.airconnect.legacy");

        List<String> audiences = properties.resolveAllowedAudiences();

        assertThat(audiences).containsExactly("com.airconnect.app", "com.airconnect.web", "com.airconnect.legacy");
    }

    @Test
    void resolveRevokeClientId_prioritizesRevokeClientId() {
        AppleAuthProperties properties = new AppleAuthProperties();
        properties.getRevoke().setClientId("com.airconnect.revoke");
        properties.setClientId("com.airconnect.legacy");
        properties.setServiceId("com.airconnect.web");
        properties.setBundleId("com.airconnect.app");

        String revokeClientId = properties.resolveRevokeClientId();

        assertThat(revokeClientId).isEqualTo("com.airconnect.revoke");
    }
}
