package univ.airconnect.user.dto.request;

import jakarta.validation.constraints.Size;
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
    private Integer age;
    @Size(max = 10)
    private String mbti;
    @Size(max = 20)
    private String smoking;
    private Gender gender;
    private MilitaryStatus military;
    @Size(max = 100)
    private String residence;
    @Size(max = 500)
    private String intro;
    @Size(max = 200)
    private String instagram;
}
