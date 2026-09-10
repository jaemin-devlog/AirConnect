package univ.airconnect.chat.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import univ.airconnect.chat.domain.entity.ChatDeliveryEvent;
import univ.airconnect.chat.dto.response.ChatMessageResponse;
import univ.airconnect.chat.dto.response.ChatRoomListUpdateResponse;
import univ.airconnect.chat.repository.*;
import univ.airconnect.notification.service.NotificationService;
import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ChatDeliveryDispatcher {
    private final ChatDeliveryEventRepository events;
    private final ChatMessageRepository messages;
    private final ChatRoomMemberRepository members;
    private final ChatRoomRepository rooms;
    private final NotificationService notifications;
    private final ObjectMapper mapper;
    private final RedisTemplate<String, Object> redis;
    private final SimpMessageSendingOperations broker;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deliver(Long id) {
        var event = events.findByIdForUpdate(id).orElse(null);
        if (event == null || event.getNextAttemptAt().isAfter(LocalDateTime.now(Clock.systemUTC()))
                || events.existsByLaneAndIdLessThan(event.getLane(), id)) return;
        try {
            switch (event.getKind()) {
                case MESSAGE -> publishMessage(event);
                case ROOM_LIST -> {
                    if (members.existsByChatRoomIdAndUserIdAndHiddenAtIsNull(event.getRoomId(), event.getUserId())) {
                        var room = rooms.findById(event.getRoomId()).orElse(null);
                        if (room != null) {
                            int unread = messages.countUnreadByUserId(event.getUserId()).stream()
                                    .filter(row -> event.getRoomId().equals(row[0]))
                                    .mapToInt(row -> ((Number) row[1]).intValue()).findFirst().orElse(0);
                            broker.convertAndSend("/sub/chat/list/" + event.getUserId(), ChatRoomListUpdateResponse.of(
                                    event.getUserId(), room.getId(), room.getLastMessage(), room.getLastMessageAt(), unread));
                        }
                    }
                }
                case NOTIFICATION -> {
                    var message = messages.findById(event.getMessageId()).orElse(null);
                    var member = members.findByChatRoomIdAndUserIdAndHiddenAtIsNull(event.getRoomId(), event.getUserId()).orElse(null);
                    if (message != null && !message.isDeleted() && member != null
                            && member.getUser().getStatus() == univ.airconnect.user.domain.UserStatus.ACTIVE
                            && !message.getCreatedAt().isBefore(member.getJoinedAt())) {
                        notifications.createAndEnqueue(mapper.readValue(event.getPayloadJson(), NotificationService.CreateCommand.class));
                    }
                }
            }
            events.delete(event);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Invalid stored chat delivery event", failure);
        }
    }

    private void publishMessage(ChatDeliveryEvent event) throws JsonProcessingException {
        if (!rooms.existsById(event.getRoomId())) return;
        var current = messages.findById(event.getMessageId()).orElse(null);
        if (current == null) return;
        var response = mapper.readValue(event.getPayloadJson(), ChatMessageResponse.class);
        // A delayed original MESSAGE must never reveal content deleted while Redis was down.
        if ("MESSAGE".equals(response.getEventType()) && current.isDeleted()) {
            response = ChatMessageResponse.from(current, response.getSenderProfileImage(), response.getUnreadCount());
        }
        redis.convertAndSend(event.getRoomId().toString(), mapper.writeValueAsString(response));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void defer(Long id) {
        events.findByIdForUpdate(id).ifPresent(ChatDeliveryEvent::defer);
    }
}
