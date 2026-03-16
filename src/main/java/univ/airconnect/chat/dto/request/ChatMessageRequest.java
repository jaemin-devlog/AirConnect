package univ.airconnect.chat.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ChatMessageRequest {

    @NotNull(message = "채팅방 ID는 필수입니다.")
    private Long roomId;

    @NotBlank(message = "메시지 내용은 비어있을 수 없습니다.")
    @Size(max = 2000, message = "메시지는 2000자 이내여야 합니다.")
    private String message;
}