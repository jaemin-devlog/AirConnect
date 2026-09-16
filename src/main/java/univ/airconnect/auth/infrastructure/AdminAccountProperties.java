package univ.airconnect.auth.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import univ.airconnect.user.domain.AdmissionYear;

@ConfigurationProperties(prefix = "auth.admin-account")
public record AdminAccountProperties(
        boolean enabled,
        String email,
        String password,
        String name,
        String nickname,
        String deptName,
        Integer studentNum
) {

    public boolean isConfigured() {
        return hasText(email) && hasText(password);
    }

    public String resolvedName() {
        return hasText(name) ? name : "AirConnect Admin";
    }

    public String resolvedNickname() {
        return hasText(nickname) ? nickname : "admin";
    }

    public String resolvedDeptName() {
        return hasText(deptName) ? deptName : "운영팀";
    }

    public Integer resolvedStudentNum() {
        Integer admissionYear = AdmissionYear.from(studentNum);
        return admissionYear != null ? admissionYear : 99;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
