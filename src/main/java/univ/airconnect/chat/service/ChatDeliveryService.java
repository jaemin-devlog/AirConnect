package univ.airconnect.chat.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import univ.airconnect.chat.domain.entity.ChatDeliveryEvent;
import univ.airconnect.chat.repository.ChatDeliveryEventRepository;
import univ.airconnect.global.transaction.AfterCommitExecutor;
import univ.airconnect.chat.dto.response.ChatMessageResponse;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatDeliveryService {
    private final ChatDeliveryEventRepository events;
    private final ChatDeliveryDispatcher dispatcher;
    private final ObjectMapper mapper;

    /** Keep the wire format per message, but amortize DB work in bounded transactions. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueueReadReceipts(Long roomId, List<ChatMessageResponse> receipts) {
        List<Long> ids = new ArrayList<>();
        try {
            for (var receipt : receipts) {
                if (!"READ_RECEIPT".equals(receipt.getEventType()) || !roomId.equals(receipt.getRoomId())) {
                    throw new IllegalArgumentException("Expected read receipts for one room");
                }
                ids.add(events.save(ChatDeliveryEvent.create(ChatDeliveryEvent.Kind.MESSAGE,
                        roomId, receipt.getId(), null, mapper.writeValueAsString(receipt))).getId());
            }
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Cannot serialize chat read receipts", failure);
        }
        for (int from = 0; from < ids.size(); from += 100) {
            List<Long> batch = List.copyOf(ids.subList(from, Math.min(from + 100, ids.size())));
            AfterCommitExecutor.execute("chat-delivery:READ_RECEIPT", () -> dispatcher.deliverReadReceipts(batch));
        }
    }

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
