package univ.airconnect.user.dto.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProfileRequest {

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
}

