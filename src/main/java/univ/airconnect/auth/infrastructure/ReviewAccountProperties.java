package univ.airconnect.auth.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import univ.airconnect.user.domain.Gender;
import univ.airconnect.user.domain.MilitaryStatus;

@ConfigurationProperties(prefix = "auth.review-account")
public record ReviewAccountProperties(
        boolean enabled,
        String email,
        String password,
        String name,
        String nickname,
        String deptName,
        Integer studentNum,
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

    public boolean isConfigured() {
        return hasText(email) && hasText(password);
    }

    public String resolvedName() {
        return hasText(name) ? name : "Apple Review";
    }

    public String resolvedNickname() {
        return hasText(nickname) ? nickname : "reviewer";
    }

    public String resolvedDeptName() {
        return hasText(deptName) ? deptName : "컴퓨터공학과";
    }

    public Integer resolvedStudentNum() {
        return studentNum != null ? studentNum : 20240001;
    }

    public Integer resolvedHeight() {
        return height != null ? height : 168;
    }

    public Integer resolvedAge() {
        return age != null ? age : 24;
    }

    public String resolvedMbti() {
        return hasText(mbti) ? mbti : "ENFP";
    }

    public String resolvedSmoking() {
        return hasText(smoking) ? smoking : "NO";
    }

    public Gender resolvedGender() {
        return gender != null ? gender : Gender.FEMALE;
    }

    public MilitaryStatus resolvedMilitary() {
        return military != null ? military : MilitaryStatus.NOT_APPLICABLE;
    }

    public String resolvedReligion() {
        return hasText(religion) ? religion : "NONE";
    }

    public String resolvedResidence() {
        return hasText(residence) ? residence : "서울 강남구";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
