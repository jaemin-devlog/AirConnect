package univ.airconnect.chat.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ChatRoomNameUpdateRequest {

    @NotBlank(message = "채팅방 이름은 비어 있을 수 없습니다.")
    @Size(max = 100, message = "채팅방 이름은 100자 이하여야 합니다.")
    private String name;
}
