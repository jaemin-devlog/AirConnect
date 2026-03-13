package univ.airconnect.user.domain.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "user_profiles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserProfile {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 100)
    private String nickname;

    @Column(length = 20)
    private String gender;

    @Column(length = 100)
    private String department;

    @Column
    private Integer birthYear;

    @Column
    private Integer height;

    @Column(length = 10)
    private String mbti;

    @Column(length = 20)
    private String smoking;

    @Column(length = 20)
    private String military;

    @Column(length = 50)
    private String religion;

    @Column(length = 100)
    private String residence;

    @Column(length = 500)
    private String intro;

    @Column(length = 100)
    private String contactStyle;

    @Column(length = 50)
    private String stdNum;

    @Column(length = 255)
    private String profileImageKey;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private UserProfile(
            User user,
            String nickname,
            String gender,
            String department,
            Integer birthYear,
            Integer height,
            String mbti,
            String smoking,
            String military,
            String religion,
            String residence,
            String intro,
            String contactStyle,
            String stdNum,
            String profileImageKey,
            LocalDateTime updatedAt
    ) {
        this.user = user;
        this.nickname = nickname;
        this.gender = gender;
        this.department = department;
        this.birthYear = birthYear;
        this.height = height;
        this.mbti = mbti;
        this.smoking = smoking;
        this.military = military;
        this.religion = religion;
        this.residence = residence;
        this.intro = intro;
        this.contactStyle = contactStyle;
        this.profileImageKey = profileImageKey;
        this.updatedAt = updatedAt;
    }

    public static UserProfile create(
            User user,
            String nickname,
            String gender,
            String department,
            Integer birthYear,
            Integer height,
            String mbti,
            String smoking,
            String military,
            String religion,
            String residence,
            String intro,
            String contactStyle,
            String profileImageKey
    ) {
        return UserProfile.builder()
                .user(user)
                .nickname(nickname)
                .gender(gender)
                .department(department)
                .birthYear(birthYear)
                .height(height)
                .mbti(mbti)
                .smoking(smoking)
                .military(military)
                .religion(religion)
                .residence(residence)
                .intro(intro)
                .contactStyle(contactStyle)
                .profileImageKey(profileImageKey)
                .updatedAt(LocalDateTime.now())
                .build();
    }

    public void update(
            String nickname,
            String gender,
            String department,
            Integer birthYear,
            Integer height,
            String mbti,
            String smoking,
            String military,
            String religion,
            String residence,
            String intro,
            String contactStyle,
            String profileImageKey
    ) {
        this.nickname = nickname;
        this.gender = gender;
        this.department = department;
        this.birthYear = birthYear;
        this.height = height;
        this.mbti = mbti;
        this.smoking = smoking;
        this.military = military;
        this.religion = religion;
        this.residence = residence;
        this.intro = intro;
        this.contactStyle = contactStyle;
        this.profileImageKey = profileImageKey;
        this.updatedAt = LocalDateTime.now();
    }

    public void anonymize() {
        this.nickname = "deleted-user-" + this.userId;
        this.gender = null;
        this.department = null;
        this.birthYear = null;
        this.height = null;
        this.mbti = null;
        this.smoking = null;
        this.military = null;
        this.religion = null;
        this.residence = null;
        this.intro = null;
        this.contactStyle = null;
        this.stdNum = null;
        this.profileImageKey = null;
        this.updatedAt = LocalDateTime.now();
    }
}