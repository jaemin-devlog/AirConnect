package univ.airconnect.moderation.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import univ.airconnect.moderation.dto.response.SupportInfoResponse;
import univ.airconnect.moderation.infrastructure.ModerationProperties;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ModerationSupportService {

    private final ModerationProperties moderationProperties;

    public SupportInfoResponse getSupportInfo() {
        ModerationProperties.Support support = moderationProperties.getSupport();
        return SupportInfoResponse.builder()
                .supportEmail(normalizeNullableText(support.getEmail()))
                .supportUrl(normalizeNullableText(support.getUrl()))
                .contactText(normalizeNullableText(support.getContactText()))
                .build();
    }

    private String normalizeNullableText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
