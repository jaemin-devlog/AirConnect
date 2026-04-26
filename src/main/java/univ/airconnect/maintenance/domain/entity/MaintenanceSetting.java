package univ.airconnect.maintenance.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "maintenance_settings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MaintenanceSetting {

    public static final long SINGLETON_ID = 1L;
    private static final String DEFAULT_TITLE = "서버 점검 중";
    private static final String DEFAULT_MESSAGE = "더 나은 서비스를 위해 점검 중입니다. 잠시 후 다시 시도해 주세요.";

    @Id
    private Long id;

    @Column(nullable = false)
    private boolean enabled;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 500)
    private String message;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "updated_by_user_id")
    private Long updatedByUserId;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static MaintenanceSetting defaultValue() {
        MaintenanceSetting setting = new MaintenanceSetting();
        setting.id = SINGLETON_ID;
        setting.enabled = false;
        setting.title = DEFAULT_TITLE;
        setting.message = DEFAULT_MESSAGE;
        setting.updatedAt = LocalDateTime.now();
        return setting;
    }

    public void update(boolean enabled, String title, String message, Long updatedByUserId) {
        boolean wasEnabled = this.enabled;
        this.enabled = enabled;
        this.title = normalizeText(title, DEFAULT_TITLE);
        this.message = normalizeText(message, DEFAULT_MESSAGE);
        this.updatedByUserId = updatedByUserId;
        this.updatedAt = LocalDateTime.now();

        if (enabled) {
            this.startedAt = wasEnabled && this.startedAt != null ? this.startedAt : LocalDateTime.now();
            return;
        }
        this.startedAt = null;
    }

    private String normalizeText(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }
}
