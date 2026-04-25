package univ.airconnect.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import univ.airconnect.notification.service.fcm.FcmDataPayloadMapper;
import univ.airconnect.notification.service.fcm.FcmDataPayloadValidationException;
import univ.airconnect.notification.service.fcm.FcmDataPayloadValidator;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FcmDataPayloadMapperTest {

    private FcmDataPayloadMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new FcmDataPayloadMapper(new ObjectMapper(), new FcmDataPayloadValidator());
    }

    @Test
    void toAndroidMap_keepsOnlyAllowedContractKeys() {
        String json = """
                {
                  "schemaVersion": "android-fcm-v1",
                  "notificationId": "10",
                  "notificationType": "CHAT_MESSAGE_RECEIVED",
                  "type": "CHAT_MESSAGE",
                  "title": "새 메시지",
                  "body": "메시지가 도착했습니다",
                  "deeplink": "/chat/1",
                  "resourceType": "CHAT_ROOM",
                  "resourceId": "1",
                  "enqueuedAt": "2026-04-25T00:00:00",
                  "unknownDomainField": "ignored"
                }
                """;

        Map<String, String> data = mapper.toAndroidMap(json);

        assertThat(data).containsEntry("schemaVersion", "android-fcm-v1");
        assertThat(data).doesNotContainKey("unknownDomainField");
    }

    @Test
    void toAndroidMap_rejectsReservedKey() {
        String json = """
                {
                  "schemaVersion": "android-fcm-v1",
                  "notificationId": "10",
                  "notificationType": "SYSTEM_ANNOUNCEMENT",
                  "type": "NOTICE",
                  "title": "공지",
                  "body": "본문",
                  "deeplink": "/notifications/10",
                  "resourceType": "SYSTEM",
                  "resourceId": "10",
                  "enqueuedAt": "2026-04-25T00:00:00",
                  "google.foo": "blocked"
                }
                """;

        assertThatThrownBy(() -> mapper.toAndroidMap(json))
                .isInstanceOf(FcmDataPayloadValidationException.class);
    }

    @Test
    void toAndroidMap_backfillsMatchRequestResourceIdFromConnectionId() {
        String json = """
                {
                  "schemaVersion": "android-fcm-v1",
                  "notificationId": "10",
                  "notificationType": "MATCH_REQUEST_RECEIVED",
                  "type": "SYSTEM",
                  "title": "매칭 요청",
                  "body": "새 요청이 도착했습니다",
                  "deeplink": "/matching/requests",
                  "connectionId": "55",
                  "enqueuedAt": "2026-04-25T00:00:00"
                }
                """;

        Map<String, String> data = mapper.toAndroidMap(json);

        assertThat(data).containsEntry("resourceType", "MATCH_REQUEST");
        assertThat(data).containsEntry("resourceId", "55");
    }

    @Test
    void toAndroidMap_rejectsMismatchedResourceTypeForNotificationType() {
        String json = """
                {
                  "schemaVersion": "android-fcm-v1",
                  "notificationId": "10",
                  "notificationType": "CHAT_MESSAGE_RECEIVED",
                  "type": "CHAT_MESSAGE",
                  "title": "새 메시지",
                  "body": "메시지가 도착했습니다",
                  "deeplink": "/chat/1",
                  "chatRoomId": "1",
                  "resourceType": "TEAM_ROOM",
                  "resourceId": "1",
                  "enqueuedAt": "2026-04-25T00:00:00"
                }
                """;

        assertThatThrownBy(() -> mapper.toAndroidMap(json))
                .isInstanceOf(FcmDataPayloadValidationException.class)
                .hasMessageContaining("resourceType does not match notificationType");
    }
}
