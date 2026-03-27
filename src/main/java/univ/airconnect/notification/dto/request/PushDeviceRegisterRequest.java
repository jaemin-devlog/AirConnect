package univ.airconnect.notification.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import univ.airconnect.notification.domain.PushPlatform;
import univ.airconnect.notification.domain.PushProvider;

import java.time.LocalDateTime;

/**
 * 현재 디바이스의 푸시 토큰을 등록하거나 갱신하는 요청 모델이다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PushDeviceRegisterRequest {

    @NotBlank
    private String deviceId;

    @NotNull
    private PushPlatform platform;

    private PushProvider provider;

    @NotBlank
    @JsonAlias("fcmToken")
    private String pushToken;

    private String apnsToken;

    @NotNull
    @JsonAlias("pushEnabled")
    private Boolean notificationPermissionGranted;

    private String appVersion;

    private String osVersion;

    private String locale;

    private String timezone;

    private LocalDateTime lastSeenAt;
}
