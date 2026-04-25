package univ.airconnect.notification.service;

import com.google.firebase.FirebaseException;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import univ.airconnect.notification.domain.PushPlatform;
import univ.airconnect.notification.domain.PushProvider;
import univ.airconnect.notification.domain.entity.NotificationOutbox;
import univ.airconnect.notification.service.fcm.FcmDataPayloadMapper;
import univ.airconnect.notification.service.fcm.FcmDataPayloadValidationException;
import univ.airconnect.notification.service.fcm.PushTargetPlatformResolver;
import univ.airconnect.notification.service.fcm.android.AndroidFcmMessageBuilder;
import univ.airconnect.notification.service.fcm.ios.IosFcmMessageBuilder;

import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "notification.push.fcm", name = "enabled", havingValue = "true")
public class FirebasePushNotificationSender implements PushNotificationSender {

    private static final Set<String> INVALID_TOKEN_ERROR_CODES = Set.of(
            "UNREGISTERED",
            "SENDER_ID_MISMATCH"
    );

    private static final Set<String> RETRYABLE_PLATFORM_ERROR_CODES = Set.of(
            "INTERNAL",
            "UNAVAILABLE",
            "DEADLINE_EXCEEDED",
            "RESOURCE_EXHAUSTED"
    );

    private static final Set<String> RETRYABLE_MESSAGING_ERROR_CODES = Set.of(
            "INTERNAL",
            "UNAVAILABLE",
            "QUOTA_EXCEEDED",
            "THIRD_PARTY_AUTH_ERROR"
    );

    private final FirebaseMessaging firebaseMessaging;
    private final FcmDataPayloadMapper dataPayloadMapper;
    private final PushTargetPlatformResolver pushTargetPlatformResolver;
    private final AndroidFcmMessageBuilder androidFcmMessageBuilder;
    private final IosFcmMessageBuilder iosFcmMessageBuilder;

    @Override
    public PushSendResult send(NotificationOutbox outbox) {
        if (outbox.getProvider() != PushProvider.FCM) {
            return PushSendResult.failed(
                    "UNSUPPORTED_PROVIDER",
                    "Only FCM delivery is implemented for push dispatch."
            );
        }

        try {
            String providerMessageId = firebaseMessaging.send(buildMessage(outbox));
            log.info("Push dispatched via FCM: outboxId={}, messageId={}", outbox.getId(), providerMessageId);
            return PushSendResult.success(providerMessageId);
        } catch (FcmDataPayloadValidationException e) {
            log.warn("FCM push payload validation failed: outboxId={}, message={}", outbox.getId(), e.getMessage());
            return PushSendResult.failed("INVALID_ANDROID_PAYLOAD", e.getMessage());
        } catch (FirebaseMessagingException e) {
            return mapFailure(outbox, e);
        }
    }

    private Message buildMessage(NotificationOutbox outbox) {
        PushPlatform platform = pushTargetPlatformResolver.resolve(outbox);

        if (platform == PushPlatform.ANDROID) {
            Map<String, String> data = dataPayloadMapper.toAndroidMap(outbox.getDataJson());
            return androidFcmMessageBuilder.build(outbox, data);
        }

        Map<String, String> data = dataPayloadMapper.toMap(outbox.getDataJson());
        if (platform == PushPlatform.IOS) {
            return iosFcmMessageBuilder.build(outbox, data);
        }
        return buildGenericMessage(outbox, data);
    }

    private Message buildGenericMessage(NotificationOutbox outbox, Map<String, String> data) {
        Message.Builder builder = Message.builder()
                .setToken(outbox.getTargetToken())
                .setNotification(com.google.firebase.messaging.Notification.builder()
                        .setTitle(outbox.getTitle())
                        .setBody(outbox.getBody())
                        .build());

        if (!data.isEmpty()) {
            builder.putAllData(data);
        }
        return builder.build();
    }

    PushSendResult mapFailure(NotificationOutbox outbox, FirebaseMessagingException e) {
        String platformErrorCode = enumName(e);
        String messagingErrorCode = e.getMessagingErrorCode() != null ? e.getMessagingErrorCode().name() : null;
        String errorCode = messagingErrorCode != null ? messagingErrorCode : platformErrorCode;
        String errorMessage = e.getMessage() != null ? e.getMessage() : "Firebase messaging request failed";

        log.warn("FCM push failed: outboxId={}, errorCode={}, messagingErrorCode={}, message={}",
                outbox.getId(), platformErrorCode, messagingErrorCode, errorMessage);

        if (isInvalidTokenFailure(messagingErrorCode, errorMessage)) {
            return PushSendResult.invalidToken(errorCode, errorMessage);
        }
        if (isRetryableFailure(platformErrorCode, messagingErrorCode)) {
            return PushSendResult.retryableFailure(errorCode, errorMessage);
        }
        return PushSendResult.failed(errorCode, errorMessage);
    }

    private boolean isInvalidTokenFailure(String messagingErrorCode, String errorMessage) {
        if (messagingErrorCode != null && INVALID_TOKEN_ERROR_CODES.contains(messagingErrorCode)) {
            return true;
        }
        return "INVALID_ARGUMENT".equals(messagingErrorCode)
                && isClearlyRegistrationTokenProblem(errorMessage);
    }

    private boolean isClearlyRegistrationTokenProblem(String errorMessage) {
        if (errorMessage == null || errorMessage.isBlank()) {
            return false;
        }
        String normalized = errorMessage.toLowerCase();
        if (!normalized.contains("registration token")) {
            return false;
        }
        return normalized.contains("invalid")
                || normalized.contains("not a valid")
                || normalized.contains("malformed")
                || normalized.contains("must be a non-empty string")
                || normalized.contains("not registered");
    }

    private boolean isRetryableFailure(String platformErrorCode, String messagingErrorCode) {
        return RETRYABLE_PLATFORM_ERROR_CODES.contains(platformErrorCode)
                || (messagingErrorCode != null && RETRYABLE_MESSAGING_ERROR_CODES.contains(messagingErrorCode));
    }

    private String enumName(FirebaseException exception) {
        return exception.getErrorCode() != null ? exception.getErrorCode().name() : "FCM_ERROR";
    }
}
