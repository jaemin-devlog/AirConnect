package univ.airconnect.user.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import univ.airconnect.user.domain.Gender;
import univ.airconnect.user.domain.MilitaryStatus;

import java.time.LocalDateTime;

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

    @Column
    private Integer height;

    @Column
    private Integer age;

    @Column(length = 10)
    private String mbti;
    
    

    @Column(length = 20)
    private String smoking;

    @Column(length = 20)
    @Enumerated(EnumType.STRING)
    private Gender gender;

    @Column(length = 20)
    @Enumerated(EnumType.STRING)
    private MilitaryStatus military;

    @Column(length = 50)
    private String religion;

    @Column(length = 100)
    private String residence;

    @Column(length = 500)
    private String intro;

    @Column(length = 200)
    private String instagram;

    @Column(length = 500)
    private String profileImagePath;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private UserProfile(
            User user,
            Integer height,
            Integer age,
            String mbti,
            String smoking,
            Gender gender,
            MilitaryStatus military,
            String religion,
            String residence,
            String intro,
            String instagram,
            String profileImagePath,
            LocalDateTime updatedAt
    ) {
        this.user = user;
        this.height = height;
        this.age = age;
        this.mbti = mbti;
        this.smoking = smoking;
        this.gender = gender;
        this.military = military;
        this.religion = religion;
        this.residence = residence;
        this.intro = intro;
        this.instagram = instagram;
        this.profileImagePath = profileImagePath;
        this.updatedAt = updatedAt;
    }

    public static UserProfile create(
            User user,
            Integer height,
            Integer age,
            String mbti,
            String smoking,
            Gender gender,
            MilitaryStatus military,
            String religion,
            String residence,
            String intro,
            String instagram
    ) {
        return UserProfile.builder()
                .user(user)
                .height(height)
                .age(age)
                .mbti(mbti)
                .smoking(smoking)
                .gender(gender)
                .military(military)
                .religion(religion)
                .residence(residence)
                .intro(intro)
                .instagram(instagram)
                .updatedAt(LocalDateTime.now())
                .build();
    }

    public void update(
            Integer height,
            Integer age,
            String mbti,
            String smoking,
            Gender gender,
            MilitaryStatus military,
            String religion,
            String residence,
            String intro,
            String instagram
    ) {
        this.height = height;
        this.age = age;
        this.mbti = mbti;
        this.smoking = smoking;
        this.gender = gender;
        this.military = military;
        this.religion = religion;
        this.residence = residence;
        this.intro = intro;
        this.instagram = instagram;
        this.updatedAt = LocalDateTime.now();
    }

    public void updatePartially(
            Integer height,
            Integer age,
            String mbti,
            String smoking,
            Gender gender,
            MilitaryStatus military,
            String religion,
            String residence,
            String intro,
            String instagram
    ) {
        if (height != null) {
            this.height = height;
        }
        if (age != null) {
            this.age = age;
        }
        if (mbti != null) {
            this.mbti = mbti;
        }
        if (smoking != null) {
            this.smoking = smoking;
        }
        if (gender != null) {
            this.gender = gender;
        }
        if (military != null) {
            this.military = military;
        }
        if (religion != null) {
            this.religion = religion;
        }
        if (residence != null) {
            this.residence = residence;
        }
        if (intro != null) {
            this.intro = intro;
        }
        if (instagram != null) {
            this.instagram = instagram;
        }
        this.updatedAt = LocalDateTime.now();
    }

    public void anonymize() {
        this.height = null;
        this.age = null;
        this.mbti = null;
        this.smoking = null;
        this.gender = null;
        this.military = null;
        this.religion = null;
        this.residence = null;
        this.intro = null;
        this.instagram = null;
        this.profileImagePath = null;
        this.updatedAt = LocalDateTime.now();
    }

    public void updateProfileImagePath(String profileImagePath) {
        this.profileImagePath = profileImagePath;
        this.updatedAt = LocalDateTime.now();
    }
}
