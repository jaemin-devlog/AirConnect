package univ.airconnect.chat.dto.request;

import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ChatReadRequest {

    @Positive(message = "마지막 읽은 메시지 ID는 양수여야 합니다.")
    private Long lastReadMessageId;
}
