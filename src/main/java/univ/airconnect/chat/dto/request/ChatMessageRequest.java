package univ.airconnect.chat.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;
import univ.airconnect.chat.domain.MessageType;

@Getter
@NoArgsConstructor
public class ChatMessageRequest {
    private Long roomId;
    private String message;
    private MessageType type;
}
