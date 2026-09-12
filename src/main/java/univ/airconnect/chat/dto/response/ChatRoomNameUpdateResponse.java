package univ.airconnect.chat.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ChatRoomNameUpdateResponse {

    private Long roomId;
    private String name;

    public static ChatRoomNameUpdateResponse of(Long roomId, String name) {
        return ChatRoomNameUpdateResponse.builder()
                .roomId(roomId)
                .name(name)
                .build();
    }
}
