package univ.airconnect.chat.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import univ.airconnect.user.domain.Gender;
import univ.airconnect.user.dto.response.UserProfileResponse;

@Getter
@Builder
@AllArgsConstructor
public class ChatParticipantDetailResponse {

    private Long userId;
    private String nickname;
    private String deptName;
    private Integer age;
    private Gender gender;
    private String profileImage;
    private boolean profileExists;
    private boolean profileImageUploaded;
    private UserProfileResponse profile;
}
