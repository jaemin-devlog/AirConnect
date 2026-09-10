package univ.airconnect.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import univ.airconnect.chat.domain.entity.ChatDeliveryEvent;
import univ.airconnect.notification.service.NotificationService;

/** Immediate delivery fake for ChatService unit tests; durable delivery is tested with real JPA separately. */
final class ChatDeliveryTestSupport {
    static ChatDeliveryService immediate(NotificationService notifications, RedisTemplate<String, Object> redis,
                                         SimpMessageSendingOperations broker, ObjectMapper mapper) {
        return new ChatDeliveryService(null, null, mapper) {
            @Override public void enqueue(ChatDeliveryEvent.Kind kind, Long room, Long message, Long user, Object payload) {
                try {
                    switch (kind) {
                        case MESSAGE -> redis.convertAndSend(room.toString(), mapper.writeValueAsString(payload));
                        case ROOM_LIST -> broker.convertAndSend("/sub/chat/list/" + user, payload);
                        case NOTIFICATION -> notifications.createAndEnqueue((NotificationService.CreateCommand) payload);
                    }
                } catch (Exception failure) { throw new IllegalStateException(failure); }
            }
        };
    }
}
