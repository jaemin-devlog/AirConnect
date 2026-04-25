package univ.airconnect.notification.service.fcm.ios;

import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.Aps;
import com.google.firebase.messaging.Message;
import org.springframework.stereotype.Component;
import univ.airconnect.notification.domain.entity.NotificationOutbox;

import java.util.Map;

@Component
public class IosFcmMessageBuilder {

    public Message build(NotificationOutbox outbox, Map<String, String> data) {
        Message.Builder builder = Message.builder()
                .setToken(outbox.getTargetToken())
                .setNotification(com.google.firebase.messaging.Notification.builder()
                        .setTitle(outbox.getTitle())
                        .setBody(outbox.getBody())
                        .build())
                .setApnsConfig(buildApnsConfig());

        if (!data.isEmpty()) {
            builder.putAllData(data);
        }
        return builder.build();
    }

    private ApnsConfig buildApnsConfig() {
        return ApnsConfig.builder()
                .putHeader("apns-priority", "10")
                .setAps(Aps.builder()
                        .setSound("default")
                        .build())
                .build();
    }
}
