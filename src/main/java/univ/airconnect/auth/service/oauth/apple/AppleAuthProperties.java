package univ.airconnect.auth.service.oauth.apple;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Getter
@Setter
@ConfigurationProperties(prefix = "apple")
public class AppleAuthProperties {

    private String teamId;
    private String keyId;
    private String clientId;
    private String bundleId;
    private String serviceId;
    private String privateKeyPath;
    private String keyPath;
    private Login login = new Login();
    private Revoke revoke = new Revoke();

    public String resolveClientId() {
        return resolveRevokeClientId();
    }

    public String resolveRevokeClientId() {
        if (revoke != null && hasText(revoke.getClientId())) {
            return revoke.getClientId();
        }
        if (clientId != null && !clientId.isBlank()) {
            return clientId;
        }
        if (serviceId != null && !serviceId.isBlank()) {
            return serviceId;
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

    public String resolveLoginIssuer() {
        if (login != null && hasText(login.getIssuer())) {
            return login.getIssuer();
        }
        return null;
    }

    public String resolveLoginJwksUrl() {
        if (login != null && hasText(login.getJwksUrl())) {
            return login.getJwksUrl();
        }
        return null;
    }

    public List<String> resolveAllowedAudiences() {
        Set<String> resolved = new LinkedHashSet<>();
        if (login != null && login.getAllowedAudiences() != null) {
            for (String audience : login.getAllowedAudiences()) {
                if (hasText(audience)) {
                    resolved.add(audience.trim());
                }
            }
        }

        if (!resolved.isEmpty()) {
            return new ArrayList<>(resolved);
        }

        if (hasText(bundleId)) {
            resolved.add(bundleId.trim());
        }
        if (hasText(serviceId)) {
            resolved.add(serviceId.trim());
        }
        if (hasText(clientId)) {
            resolved.add(clientId.trim());
        }
        return new ArrayList<>(resolved);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    @Getter
    @Setter
    public static class Login {
        private String issuer = "https://appleid.apple.com";
        private String jwksUrl = "https://appleid.apple.com/auth/keys";
        private List<String> allowedAudiences = new ArrayList<>();
    }

    @Getter
    @Setter
    public static class Revoke {
        private String clientId;
        private boolean enabled = true;
        private String endpoint = "https://appleid.apple.com/auth/revoke";
        private long clientSecretTtlSeconds = 300L;
    }
}
