package univ.airconnect.user.dto.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import univ.airconnect.user.domain.Gender;
import univ.airconnect.user.domain.MilitaryStatus;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProfileRequest {

    private Integer height;
    private String mbti;
    private String smoking;
    private Gender gender;
    private MilitaryStatus military;
    private String religion;
    private String residence;
    private String intro;
    private String instagram;
}

