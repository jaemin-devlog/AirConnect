package univ.airconnect.user.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import univ.airconnect.user.domain.entity.UserProfile;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class UserProfileResponse {

    private Long userId;
    private String nickname;
    private String gender;
    private String department;
    private Integer birthYear;
    private Integer height;
    private String mbti;
    private String smoking;
    private String military;
    private String religion;
    private String residence;
    private String intro;
    private String contactStyle;
    private String profileImageKey;
    private LocalDateTime updatedAt;

    public static UserProfileResponse from(UserProfile userProfile) {
        return UserProfileResponse.builder()
                .userId(userProfile.getUserId())
                .nickname(userProfile.getNickname())
                .gender(userProfile.getGender())
                .department(userProfile.getDepartment())
                .birthYear(userProfile.getBirthYear())
                .height(userProfile.getHeight())
                .mbti(userProfile.getMbti())
                .smoking(userProfile.getSmoking())
                .military(userProfile.getMilitary())
                .religion(userProfile.getReligion())
                .residence(userProfile.getResidence())
                .intro(userProfile.getIntro())
                .contactStyle(userProfile.getContactStyle())
                .profileImageKey(userProfile.getProfileImageKey())
                .updatedAt(userProfile.getUpdatedAt())
                .build();
    }
}

