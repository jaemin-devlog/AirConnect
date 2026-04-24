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
    private Integer age;
    private Gender gender;
    private String profileImage;
    private boolean profileExists;
    private boolean profileImageUploaded;
}
