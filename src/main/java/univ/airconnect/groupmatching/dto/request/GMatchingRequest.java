package univ.airconnect.groupmatching.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import univ.airconnect.groupmatching.domain.GTeamSize;

public final class GMatchingRequest {

    private GMatchingRequest() {
    }

    @Getter
    @NoArgsConstructor
    public static class CreateTemporaryTeamRoomRequest {

        @NotNull(message = "팀 인원은 필수입니다.")
        private GTeamSize teamSize;

    }

    @Getter
    @NoArgsConstructor
    public static class JoinByInviteCodeRequest {

        @NotBlank(message = "초대 코드는 필수입니다.")
        @Size(max = 20, message = "초대 코드는 20자 이하여야 합니다.")
        private String inviteCode;
    }


}
