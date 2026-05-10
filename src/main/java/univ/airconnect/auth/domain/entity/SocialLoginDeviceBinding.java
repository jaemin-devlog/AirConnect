package univ.airconnect.auth.domain.entity;

import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "social_login_device_bindings",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_social_login_device_bindings_device", columnNames = "device_id")
        },
        indexes = {
                @Index(name = "idx_social_login_device_bindings_user", columnList = "user_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Access(AccessType.FIELD)
public class SocialLoginDeviceBinding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "device_id", nullable = false, length = 120)
    private String deviceId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private SocialLoginDeviceBinding(Long userId, String deviceId) {
        if (userId == null) {
            throw new IllegalArgumentException("사용자 ID는 필수입니다.");
        }
        if (deviceId == null || deviceId.isBlank()) {
            throw new IllegalArgumentException("디바이스 ID는 필수입니다.");
        }

        LocalDateTime now = LocalDateTime.now();
        this.userId = userId;
        this.deviceId = deviceId;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static SocialLoginDeviceBinding bind(Long userId, String deviceId) {
        return new SocialLoginDeviceBinding(userId, deviceId);
    }

    public void touch() {
        this.updatedAt = LocalDateTime.now();
    }
}
