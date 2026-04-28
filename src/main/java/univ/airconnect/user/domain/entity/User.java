package univ.airconnect.user.domain.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import univ.airconnect.auth.domain.entity.SocialProvider;
import univ.airconnect.user.domain.OnboardingStatus;
import univ.airconnect.user.domain.UserRole;
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

    private static final int INITIAL_TICKETS = 10;

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

    @Column(length = 255)
    private String verifiedSchoolEmail;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

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

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private UserRole role;

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

    @Column(nullable = false)
    private Integer tickets;

    @Column(name = "ios_app_account_token", nullable = false, unique = true, length = 36)
    private String iosAppAccountToken;

    @OneToOne(mappedBy = "user", fetch = FetchType.LAZY)
    private UserProfile userProfile;

    @Builder
    private User(
            Long id,
            SocialProvider provider,
            String socialId,
            String email,
            String verifiedSchoolEmail,
            String passwordHash,
            String name,
            String nickname,
            String deptName,
            Integer studentNum,
            UserStatus status,
            OnboardingStatus onboardingStatus,
            UserRole role,
            LocalDateTime createdAt,
            LocalDateTime lastActiveAt,
            LocalDateTime deletedAt,
            LocalDateTime suspendedUntil,
            LocalDateTime restrictedAt,
            LocalDateTime restrictedUntil,
            String restrictedReason,
            Integer tickets,
            String iosAppAccountToken
    ) {
        this.id = id;
        this.provider = provider;
        this.socialId = socialId;
        this.email = email;
        this.verifiedSchoolEmail = verifiedSchoolEmail;
        this.passwordHash = passwordHash;
        this.name = name;
        this.nickname = nickname;
        this.deptName = deptName;
        this.studentNum = studentNum;
        this.status = status;
        this.onboardingStatus = onboardingStatus;
        this.role = role != null ? role : UserRole.USER;
        this.createdAt = createdAt;
        this.lastActiveAt = lastActiveAt;
        this.deletedAt = deletedAt;
        this.suspendedUntil = suspendedUntil;
        this.restrictedAt = restrictedAt;
        this.restrictedUntil = restrictedUntil;
        this.restrictedReason = restrictedReason;
        this.tickets = tickets != null ? tickets : INITIAL_TICKETS;
        this.iosAppAccountToken = (iosAppAccountToken == null || iosAppAccountToken.isBlank())
                ? UUID.randomUUID().toString()
                : iosAppAccountToken;
    }

    public static User create(SocialProvider provider, String socialId) {
        return create(provider, socialId, null);
    }

    public static User create(SocialProvider provider, String socialId, String email) {
        return User.builder()
                .provider(provider)
                .socialId(socialId)
                .email(email)
                .verifiedSchoolEmail(null)
                .status(UserStatus.ACTIVE)
                .onboardingStatus(OnboardingStatus.BASIC)
                .role(UserRole.USER)
                .tickets(INITIAL_TICKETS)
                .iosAppAccountToken(UUID.randomUUID().toString())
                .createdAt(LocalDateTime.now())
                .build();
    }

    public static User createEmailUser(String email, String passwordHash) {
        return User.builder()
                .provider(SocialProvider.EMAIL)
                .socialId(email)
                .email(email)
                .verifiedSchoolEmail(email)
                .passwordHash(passwordHash)
                .status(UserStatus.ACTIVE)
                .onboardingStatus(OnboardingStatus.BASIC)
                .role(UserRole.USER)
                .tickets(INITIAL_TICKETS)
                .iosAppAccountToken(UUID.randomUUID().toString())
                .createdAt(LocalDateTime.now())
                .build();
    }

    public String ensureIosAppAccountToken() {
        if (this.iosAppAccountToken == null || this.iosAppAccountToken.isBlank()) {
            this.iosAppAccountToken = UUID.randomUUID().toString();
        }
        return this.iosAppAccountToken;
    }

    public void completeSignUp(String name, String nickname, Integer studentNum, String deptName) {
        this.name = name;
        this.nickname = nickname;
        this.studentNum = studentNum;
        this.deptName = deptName;
        this.onboardingStatus = OnboardingStatus.FULL;
        this.lastActiveAt = LocalDateTime.now();
    }

    public void changeNickname(String nickname) {
        this.nickname = nickname;
        this.lastActiveAt = LocalDateTime.now();
    }

    public void resetOnboarding() {
        this.name = null;
        this.nickname = null;
        this.studentNum = null;
        this.deptName = null;
        this.onboardingStatus = OnboardingStatus.BASIC;
        this.lastActiveAt = null;
    }

    public void anonymizeForDeletion() {
        this.email = null;
        this.verifiedSchoolEmail = null;
        this.passwordHash = null;
        this.name = null;
        this.nickname = null;
        this.studentNum = null;
        this.deptName = null;
        this.onboardingStatus = OnboardingStatus.BASIC;
        this.lastActiveAt = null;
        this.suspendedUntil = null;
        this.restrictedAt = null;
        this.restrictedUntil = null;
        this.restrictedReason = null;
        this.iosAppAccountToken = UUID.randomUUID().toString();
    }

    public void consumeTickets(int amount) {
        if (this.tickets < amount) {
            throw new IllegalArgumentException("티켓이 부족합니다. 현재: " + this.tickets + ", 필요: " + amount);
        }
        this.tickets -= amount;
    }

    public void addTickets(int amount) {
        this.tickets += amount;
    }

    public void deductTicketsAllowNegative(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("차감 수량은 0 이상이어야 합니다.");
        }
        this.tickets -= amount;
    }

    public void adjustTickets(int delta) {
        if (delta >= 0) {
            addTickets(delta);
            return;
        }
        consumeTickets(Math.abs(delta));
    }

    public void markActive() {
        if (this.status == UserStatus.DELETED) {
            return;
        }
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

    public boolean isEmailProvider() {
        return this.provider == SocialProvider.EMAIL;
    }

    public UserRole getRole() {
        return this.role != null ? this.role : UserRole.USER;
    }

    public boolean isAdmin() {
        return getRole() == UserRole.ADMIN;
    }

    public void changeRole(UserRole role) {
        this.role = role != null ? role : UserRole.USER;
        this.lastActiveAt = LocalDateTime.now();
    }

    public void changePasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
        this.lastActiveAt = LocalDateTime.now();
    }

    public void updateSocialEmail(String email) {
        this.email = email;
    }

    public void updateVerifiedSchoolEmail(String verifiedSchoolEmail) {
        this.verifiedSchoolEmail = verifiedSchoolEmail;
    }

    public boolean hasVerifiedSchoolEmail() {
        return this.verifiedSchoolEmail != null && !this.verifiedSchoolEmail.isBlank();
    }

    public String getPrimaryEmail() {
        if (hasVerifiedSchoolEmail()) {
            return this.verifiedSchoolEmail;
        }
        return this.email;
    }

    public void suspend(LocalDateTime until, String reason) {
        if (this.status == UserStatus.DELETED) {
            return;
        }
        this.status = UserStatus.SUSPENDED;
        this.suspendedUntil = until;
        this.lastActiveAt = null;
    }

    public void reactivate() {
        if (this.status == UserStatus.DELETED) {
            return;
        }
        this.status = UserStatus.ACTIVE;
        this.suspendedUntil = null;
    }

    public void restrictMatching(LocalDateTime until, String reason) {
        this.restrictedAt = LocalDateTime.now();
        this.restrictedUntil = until;
        this.restrictedReason = reason;
    }

    public void clearMatchingRestriction() {
        this.restrictedAt = null;
        this.restrictedUntil = null;
        this.restrictedReason = null;
    }

    public boolean isMatchingRestricted() {
        if (this.restrictedAt == null) {
            return false;
        }
        return this.restrictedUntil == null || this.restrictedUntil.isAfter(LocalDateTime.now());
    }
}
