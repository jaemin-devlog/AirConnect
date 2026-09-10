package univ.airconnect.chat.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import univ.airconnect.chat.domain.entity.ChatDeliveryEvent;
import univ.airconnect.chat.repository.ChatDeliveryEventRepository;
import univ.airconnect.global.transaction.AfterCommitExecutor;

@Service
@RequiredArgsConstructor
public class ChatDeliveryService {
    private final ChatDeliveryEventRepository events;
    private final ChatDeliveryDispatcher dispatcher;
    private final ObjectMapper mapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(ChatDeliveryEvent.Kind kind, Long roomId, Long messageId, Long userId, Object payload) {
        try {
            var event = events.save(ChatDeliveryEvent.create(kind, roomId, messageId, userId,
                    mapper.writeValueAsString(payload)));
            // Fast path after commit. Failure leaves the event for the scheduled retry worker.
            AfterCommitExecutor.execute("chat-delivery:" + kind, () -> dispatcher.deliver(event.getId()));
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Cannot serialize chat delivery intent", failure);
        }
    }
}
