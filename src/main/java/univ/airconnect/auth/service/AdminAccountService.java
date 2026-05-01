package univ.airconnect.auth.service;

import java.util.Locale;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import univ.airconnect.auth.infrastructure.AdminAccountProperties;

@Service
@RequiredArgsConstructor
public class AdminAccountService {

    private final AdminAccountProperties properties;

    public boolean isEnabledAndConfigured() {
        return properties.enabled() && properties.isConfigured();
    }

    public String normalizedEmail() {
        return normalizeEmail(properties.email());
    }

    public String rawPassword() {
        return properties.password();
    }

    public String loginAttemptIdentifier() {
        return isEnabledAndConfigured() ? normalizedEmail() : "admin-account";
    }

    public AdminAccountProperties properties() {
        return properties;
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
