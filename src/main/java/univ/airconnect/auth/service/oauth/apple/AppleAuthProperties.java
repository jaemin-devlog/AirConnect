package univ.airconnect.auth.service.oauth.apple;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "apple")
public class AppleAuthProperties {

    private String teamId;
    private String keyId;
    private String clientId;
    private String bundleId;
    private String privateKeyPath;
    private String keyPath;
    private Revoke revoke = new Revoke();

    public String resolveClientId() {
        if (clientId != null && !clientId.isBlank()) {
            return clientId;
        }
        if (bundleId != null && !bundleId.isBlank()) {
            return bundleId;
        }
        return null;
    }

    public String resolvePrivateKeyPath() {
        if (privateKeyPath != null && !privateKeyPath.isBlank()) {
            return privateKeyPath;
        }
        if (keyPath != null && !keyPath.isBlank()) {
            return keyPath;
        }
        return null;
    }

    @Getter
    @Setter
    public static class Revoke {
        private boolean enabled = true;
        private String endpoint = "https://appleid.apple.com/auth/revoke";
        private long clientSecretTtlSeconds = 300L;
    }
}
