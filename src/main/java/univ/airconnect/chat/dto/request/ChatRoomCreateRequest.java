package univ.airconnect.chat.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;
import univ.airconnect.chat.domain.ChatRoomType;

@Getter
@NoArgsConstructor
public class ChatRoomCreateRequest {

    @NotBlank(message = "채팅방 이름은 비어 있을 수 없습니다.")
    private String name;

    @NotNull(message = "채팅방 타입은 필수입니다.")
    private ChatRoomType type;

    @Positive(message = "상대방 ID는 양수여야 합니다.")
    private Long targetUserId;
}
