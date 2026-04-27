package univ.airconnect.verification.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "verified_school_emails",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_verified_school_emails_email", columnNames = "email")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VerifiedSchoolEmail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String email;

    @Column(name = "linked_user_id")
    private Long linkedUserId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private VerifiedSchoolEmail(String email, Long linkedUserId) {
        this.email = email;
        this.linkedUserId = linkedUserId;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public static VerifiedSchoolEmail reserve(String email, Long linkedUserId) {
        return new VerifiedSchoolEmail(email, linkedUserId);
    }

    public void linkToUser(Long userId) {
        if (userId == null) {
            return;
        }
        this.linkedUserId = userId;
        this.updatedAt = LocalDateTime.now();
    }
}
