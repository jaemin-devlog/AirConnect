package univ.airconnect.chat.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import univ.airconnect.chat.domain.MessageType;

@Getter
@NoArgsConstructor
public class ChatMessageRequest {

    @NotNull(message = "채팅방 ID는 필수입니다.")
    private Long roomId;

    @JsonAlias("content")
    @NotBlank(message = "메시지 내용은 비어있을 수 없습니다.")
    @Size(max = 2000, message = "메시지는 2000자 이내여야 합니다.")
    private String message;

    private MessageType messageType;

    @Size(max = 64, message = "클라이언트 메시지 ID는 64자 이내여야 합니다.")
    private String clientMessageId;
}
