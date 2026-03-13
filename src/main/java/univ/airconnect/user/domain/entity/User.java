package univ.airconnect.user.domain.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import univ.airconnect.auth.domain.entity.SocialProvider;
import univ.airconnect.user.domain.OnboardingStatus;
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

    @Column(length = 100)
    private String name;

    @Column(length = 100)
    private String deptName;

    @Column(length = 100)
    private String nickname;

    @Column(length = 20)
    private Integer studentNum;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OnboardingStatus onboardingStatus;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime lastActiveAt;

    @Column
    private LocalDateTime deletedAt;

    @Column
    private LocalDateTime suspendedUntil;

    @Column
    private LocalDateTime restrictedAt;

    @Column
    private LocalDateTime restrictedUntil;

    @Column(length = 500)
    private String restrictedReason;

    @OneToOne(mappedBy = "user", fetch = FetchType.LAZY)
    private UserProfile userProfile;

    @Builder
    private User(
            Long id,
            SocialProvider provider,
            String socialId,
            String email,
            String name,
            String nickname,
            String deptName,
            Integer studentNum,
            UserStatus status,
            OnboardingStatus onboardingStatus,
            LocalDateTime createdAt,
            LocalDateTime lastActiveAt,
            LocalDateTime deletedAt,
            LocalDateTime suspendedUntil,
            LocalDateTime restrictedAt,
            LocalDateTime restrictedUntil,
            String restrictedReason
    ) {
        this.id = id;
        this.provider = provider;
        this.socialId = socialId;
        this.email = email;
        this.name = name;
        this.nickname = nickname;
        this.deptName = deptName;
        this.studentNum = studentNum;
        this.status = status;
        this.onboardingStatus = onboardingStatus;
        this.createdAt = createdAt;
        this.lastActiveAt = lastActiveAt;
        this.deletedAt = deletedAt;
        this.suspendedUntil = suspendedUntil;
        this.restrictedAt = restrictedAt;
        this.restrictedUntil = restrictedUntil;
        this.restrictedReason = restrictedReason;
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
                .onboardingStatus(OnboardingStatus.BASIC)
                .createdAt(LocalDateTime.now())
                .build();
    }

    public void completeSignUp(String name, String nickname, Integer studentNum, String deptName) {
        this.name = name;
        this.nickname = nickname;
        this.studentNum = studentNum;
        this.deptName = deptName;
        this.onboardingStatus = OnboardingStatus.FULL;
        this.lastActiveAt = LocalDateTime.now();
    }

    public void markDeleted() {
        if (this.status == UserStatus.DELETED) {
            return;
        }
        this.status = UserStatus.DELETED;
        this.deletedAt = LocalDateTime.now();
        this.lastActiveAt = null;
    }
}