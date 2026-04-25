package univ.airconnect.notification.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import univ.airconnect.notification.domain.PushEventType;

import java.time.LocalDateTime;

/**
 * 클라이언트가 보고하는 푸시 이벤트 저장 요청 모델이다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PushEventCreateRequest {

    @NotBlank
    private String notificationId;

    @Size(max = 200)
    private String providerMessageId;

    @NotNull
    private PushEventType eventType;

    @NotNull
    private LocalDateTime occurredAt;

    @NotBlank
    private String deviceId;
}
