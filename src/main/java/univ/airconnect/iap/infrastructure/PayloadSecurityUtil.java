package univ.airconnect.iap.infrastructure;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

@Component
public class PayloadSecurityUtil {

    public String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to hash payload", e);
        }
    }

    public String mask(String value) {
        if (value == null) {
            return "";
        }
        if (value.length() <= 16) {
            return "********";
        }
        String prefix = value.substring(0, 8);
        String suffix = value.substring(value.length() - 8);
        return prefix + "..." + suffix;
    }
}

