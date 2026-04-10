package univ.airconnect.auth.service;

import java.util.Locale;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import univ.airconnect.auth.dto.request.EmailLoginRequest;
import univ.airconnect.auth.infrastructure.ReviewAccountProperties;

@Service
@RequiredArgsConstructor
public class ReviewAccountService {

    private final ReviewAccountProperties properties;

    public boolean isReviewLoginRequest(EmailLoginRequest request) {
        if (request == null || !isEnabledAndConfigured() || request.getEmail() == null) {
            return false;
        }
        return normalizedEmail().equals(normalizeEmail(request.getEmail()));
    }

    public boolean isEnabledAndConfigured() {
        return properties.enabled() && properties.isConfigured();
    }

    public String normalizedEmail() {
        return normalizeEmail(properties.email());
    }

    public String rawPassword() {
        return properties.password();
    }

    public ReviewAccountProperties properties() {
        return properties;
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
