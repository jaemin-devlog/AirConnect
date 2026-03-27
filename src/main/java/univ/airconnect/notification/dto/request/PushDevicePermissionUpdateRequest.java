package univ.airconnect.notification.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 등록된 디바이스 하나의 OS 알림 권한 상태를 수정하는 요청 모델이다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PushDevicePermissionUpdateRequest {

    @NotNull
    @JsonAlias("pushEnabled")
    private Boolean notificationPermissionGranted;

    private LocalDateTime lastSeenAt;
}
