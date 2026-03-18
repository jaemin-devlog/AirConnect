package univ.airconnect.user.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import univ.airconnect.user.domain.Gender;
import univ.airconnect.user.domain.entity.UserProfile;
import univ.airconnect.user.domain.MilitaryStatus;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class UserProfileResponse {

    private Long userId;
    private Integer height;
    private String mbti;
    private String smoking;
    private Gender gender;
    private MilitaryStatus military;
    private String religion;
    private String residence;
    private String intro;
    private String instagram;
    private String profileImagePath;
    private LocalDateTime updatedAt;

    public static UserProfileResponse from(UserProfile userProfile, String imageUrlBase) {
        // 프로필 이미지 URL 생성 (파일명이 있을 경우만)
        String profileImagePath = null;
        if (userProfile.getProfileImagePath() != null && !userProfile.getProfileImagePath().isEmpty()) {
            profileImagePath = imageUrlBase + "/" + userProfile.getProfileImagePath();
        }

        return UserProfileResponse.builder()
                .userId(userProfile.getUserId())
                .height(userProfile.getHeight())
                .mbti(userProfile.getMbti())
                .smoking(userProfile.getSmoking())
                .gender(userProfile.getGender())
                .military(userProfile.getMilitary())
                .religion(userProfile.getReligion())
                .residence(userProfile.getResidence())
                .intro(userProfile.getIntro())
                .instagram(userProfile.getInstagram())
                .profileImagePath(profileImagePath)
                .updatedAt(userProfile.getUpdatedAt())
                .build();
    }
}



