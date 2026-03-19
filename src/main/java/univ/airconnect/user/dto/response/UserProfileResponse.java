package univ.airconnect.user.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import univ.airconnect.user.domain.Gender;
import univ.airconnect.user.domain.MilitaryStatus;
import univ.airconnect.user.domain.entity.UserProfile;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class UserProfileResponse {

    @JsonProperty("userId")
    private Long userId;

    @JsonProperty("height")
    private Integer height;

    @JsonProperty("mbti")
    private String mbti;

    @JsonProperty("smoking")
    private String smoking;

    @JsonProperty("gender")
    private Gender gender;

    @JsonProperty("military")
    private MilitaryStatus military;

    @JsonProperty("religion")
    private String religion;

    @JsonProperty("residence")
    private String residence;

    @JsonProperty("intro")
    private String intro;

    @JsonProperty("instagram")
    private String instagram;

    @JsonProperty("profileImagePath")
    private String profileImagePath;

    @JsonProperty("updatedAt")
    private LocalDateTime updatedAt;

    public static UserProfileResponse from(UserProfile userProfile, String imageUrlBase) {
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
