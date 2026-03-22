package univ.airconnect.chat.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import univ.airconnect.chat.domain.MessageType;

@Getter
@NoArgsConstructor
public class SendMessageRequest {

    @NotBlank(message = "메시지 내용은 비어있을 수 없습니다.")
    @Size(max = 2000, message = "메시지는 2000자 이내여야 합니다.")
    private String content;

    // 기본값은 TEXT로 처리한다.
    private MessageType messageType;
}

