package univ.airconnect.user.domain.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import univ.airconnect.user.domain.MilitaryStatus;

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

    @Column(length = 10)
    private String mbti;

    @Column(length = 20)
    private String smoking;

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

    @Column(nullable = false)
    private LocalDateTime updatedAt;


    @Builder
    private UserProfile(
            User user,
            Integer height,
            String mbti,
            String smoking,
            MilitaryStatus military,
            String religion,
            String residence,
            String intro,
            String instagram,
            LocalDateTime updatedAt
    ) {
        this.user = user;
        this.height = height;
        this.mbti = mbti;
        this.smoking = smoking;
        this.military = military;
        this.religion = religion;
        this.residence = residence;
        this.intro = intro;
        this.instagram = instagram;
        this.updatedAt = updatedAt;
    }

    public static UserProfile create(
            User user,
            Integer height,
            String mbti,
            String smoking,
            MilitaryStatus military,
            String religion,
            String residence,
            String intro,
            String instagram
    ) {
        return UserProfile.builder()
                .user(user)
                .height(height)
                .mbti(mbti)
                .smoking(smoking)
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
            String mbti,
            String smoking,
            MilitaryStatus military,
            String religion,
            String residence,
            String intro,
            String instagram
    ) {
        this.height = height;
        this.mbti = mbti;
        this.smoking = smoking;
        this.military = military;
        this.religion = religion;
        this.residence = residence;
        this.intro = intro;
        this.instagram = instagram;
        this.updatedAt = LocalDateTime.now();
    }

    public void anonymize() {
        this.height = null;
        this.mbti = null;
        this.smoking = null;
        this.military = null;
        this.religion = null;
        this.residence = null;
        this.intro = null;
        this.instagram = null;
        this.updatedAt = LocalDateTime.now();
    }
}