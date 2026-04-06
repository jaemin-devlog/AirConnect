package univ.airconnect.moderation.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SupportInfoResponse {
    private String supportEmail;
    private String supportUrl;
    private String contactText;
}
