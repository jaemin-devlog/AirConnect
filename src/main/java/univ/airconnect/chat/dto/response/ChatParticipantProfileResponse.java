package univ.airconnect.chat.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import univ.airconnect.user.domain.Gender;

@Getter
@Builder
@AllArgsConstructor
public class ChatParticipantProfileResponse {

    private Long userId;
    private String nickname;
    private String deptName;
    /** 전체 학번이 아니라 두 자리 입학 연도이다. */
    private Integer studentNum;
    private Integer age;
    private Gender gender;
    private String profileImage;
    private boolean emailVerified;
    private boolean profileExists;
    private boolean profileImageUploaded;
}
