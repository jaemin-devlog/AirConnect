package univ.airconnect.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import univ.airconnect.chat.dto.response.ChatMessageResponse;

@Slf4j
@RequiredArgsConstructor
@Service
public class RedisSubscriber implements MessageListener {

    private final ObjectMapper objectMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final SimpMessageSendingOperations messagingTemplate;

    /**
     * Redis publish 메시지를 받아 websocket 구독자에게 전달한다.
     */
    @Override

    public void onMessage(Message message, byte[] pattern) {
        log.info("Redis에서 메시지 수신 성공! 채널: {}", new String(message.getChannel()));
        try {
            String publishMessage = (String) redisTemplate.getStringSerializer().deserialize(message.getBody());

            if (!StringUtils.hasText(publishMessage)) {
                log.warn("Redis Subscribe skipped: empty payload");
                return;
            }

            ChatMessageResponse roomMessage = objectMapper.readValue(publishMessage, ChatMessageResponse.class);

            messagingTemplate.convertAndSend("/sub/chat/room/" + roomMessage.getRoomId(), roomMessage);

            log.info("Redis Subscribe: roomId={}, senderId={}", roomMessage.getRoomId(), roomMessage.getSenderId());
        } catch (Exception e) {
            log.error("Redis subscribe handling failed", e);
        }
    }
}