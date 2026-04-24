package univ.airconnect.notification.dto.response;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import univ.airconnect.notification.domain.NotificationType;
import univ.airconnect.notification.domain.entity.Notification;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationItemResponseTest {

    @Test
    void from_serializesCreatedAtWithUtcOffset() throws Exception {
        Notification notification = Notification.create(
                1L,
                NotificationType.MATCH_REQUEST_RECEIVED,
                "title",
                "body",
                "airconnect://notification/1",
                2L,
                null,
                "{\"id\":1}",
                "dedupe-1"
        );
        LocalDateTime createdAt = LocalDateTime.of(2026, 4, 12, 4, 30, 0);
        ReflectionTestUtils.setField(notification, "createdAt", createdAt);

        NotificationItemResponse response = NotificationItemResponse.from(notification, null);
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        String json = objectMapper.writeValueAsString(response);

        assertThat(response.getCreatedAt()).isEqualTo(createdAt.atOffset(ZoneOffset.UTC));
        assertThat(json).containsPattern("\"createdAt\":\"2026-04-12T04:30:00\\.000000(Z|\\+00:00)\"");
    }
}
