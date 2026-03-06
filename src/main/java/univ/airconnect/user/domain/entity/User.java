package univ.airconnect.user.domain.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import univ.airconnect.auth.domain.entity.SocialProvider;
import univ.airconnect.user.domain.UserStatus;

@Entity
@Table(
        name = "users",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"provider", "socialId"})
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SocialProvider provider;

    @Column(nullable = false, length = 100)
    private String socialId;

    @Column(length = 255)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Builder
    private User(
            Long id,
            SocialProvider provider,
            String socialId,
            String email,
            UserStatus status,
            LocalDateTime createdAt
    ) {
        this.id = id;
        this.provider = provider;
        this.socialId = socialId;
        this.email = email;
        this.status = status;
        this.createdAt = createdAt;
    }

    public static User create(SocialProvider provider, String socialId) {
        return create(provider, socialId, null);
    }

    public static User create(SocialProvider provider, String socialId, String email) {
        return User.builder()
                .provider(provider)
                .socialId(socialId)
                .email(email)
                .status(UserStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build();
    }
}