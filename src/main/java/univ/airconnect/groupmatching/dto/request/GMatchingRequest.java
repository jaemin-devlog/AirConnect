package univ.airconnect.groupmatching.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import univ.airconnect.groupmatching.domain.GGenderFilter;
import univ.airconnect.groupmatching.domain.GTeamGender;
import univ.airconnect.groupmatching.domain.GTeamSize;
import univ.airconnect.groupmatching.domain.GTeamVisibility;

public final class GMatchingRequest {

    private GMatchingRequest() {
    }

    @Getter
    @NoArgsConstructor
    public static class CreateTemporaryTeamRoomRequest {

        @NotBlank(message = "팀 이름은 필수입니다.")
        @Size(max = 100, message = "팀 이름은 100자 이하여야 합니다.")
        private String teamName;

        @NotNull(message = "팀 성별은 필수입니다.")
        private GTeamGender teamGender;

        @NotNull(message = "팀 인원은 필수입니다.")
        private GTeamSize teamSize;

        @NotNull(message = "상대 팀 성별 필터는 필수입니다.")
        private GGenderFilter opponentGenderFilter;

        @NotNull(message = "공개/비공개 설정은 필수입니다.")
        private GTeamVisibility visibility;
    }

    @Getter
    @NoArgsConstructor
    public static class JoinPrivateRoomRequest {

        @NotBlank(message = "초대 코드는 필수입니다.")
        @Size(max = 20, message = "초대 코드는 20자 이하여야 합니다.")
        private String inviteCode;
    }

    @Getter
    @NoArgsConstructor
    public static class UpdateReadyStateRequest {

        @NotNull(message = "ready 값은 필수입니다.")
        private Boolean ready;
    }
}
