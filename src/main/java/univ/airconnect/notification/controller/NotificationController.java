package univ.airconnect.notification.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import univ.airconnect.global.response.ApiResponse;
import univ.airconnect.global.security.resolver.CurrentUserId;
import univ.airconnect.notification.domain.NotificationType;
import univ.airconnect.notification.domain.entity.Notification;
import univ.airconnect.notification.domain.entity.NotificationPreference;
import univ.airconnect.notification.dto.request.NotificationPreferenceUpdateRequest;
import univ.airconnect.notification.dto.response.NotificationItemResponse;
import univ.airconnect.notification.dto.response.NotificationListResponse;
import univ.airconnect.notification.dto.response.NotificationPreferenceResponse;
import univ.airconnect.notification.dto.response.NotificationReadAllResponse;
import univ.airconnect.notification.dto.response.NotificationUnreadCountResponse;
import univ.airconnect.notification.service.NotificationInboxService;
import univ.airconnect.notification.service.NotificationPreferenceService;

import java.util.List;

import static univ.airconnect.global.web.TraceIdFilter.TRACE_ID_ATTRIBUTE;

/**
 * 모바일 앱에서 사용하는 알림함 및 알림 설정 API를 제공한다.
 */
@Validated
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationInboxService notificationInboxService;
    private final NotificationPreferenceService notificationPreferenceService;
    private final ObjectMapper objectMapper;

    @GetMapping
    public ResponseEntity<ApiResponse<NotificationListResponse>> getNotifications(
            @CurrentUserId Long userId,
            @RequestParam(required = false) Long cursorId,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false, defaultValue = "false") Boolean unreadOnly,
            @RequestParam(required = false) NotificationType type,
            HttpServletRequest request
    ) {
        String traceId = (String) request.getAttribute(TRACE_ID_ATTRIBUTE);
        NotificationInboxService.NotificationSlice slice =
                notificationInboxService.getNotifications(userId, cursorId, size, unreadOnly, type);

        List<NotificationItemResponse> items = slice.items().stream()
                .map(this::toNotificationItem)
                .toList();

        NotificationListResponse response = new NotificationListResponse(
                slice.requestedSize(),
                slice.unreadCount(),
                slice.hasNext(),
                slice.nextCursorId(),
                items.size(),
                items
        );
        return ResponseEntity.ok(ApiResponse.ok(response, traceId));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<NotificationUnreadCountResponse>> getUnreadCount(
            @CurrentUserId Long userId,
            HttpServletRequest request
    ) {
        String traceId = (String) request.getAttribute(TRACE_ID_ATTRIBUTE);
        NotificationUnreadCountResponse response =
                new NotificationUnreadCountResponse(notificationInboxService.getUnreadCount(userId));
        return ResponseEntity.ok(ApiResponse.ok(response, traceId));
    }

    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<ApiResponse<NotificationItemResponse>> markRead(
            @CurrentUserId Long userId,
            @PathVariable Long notificationId,
            HttpServletRequest request
    ) {
        String traceId = (String) request.getAttribute(TRACE_ID_ATTRIBUTE);
        Notification notification = notificationInboxService.markRead(userId, notificationId);
        return ResponseEntity.ok(ApiResponse.ok(toNotificationItem(notification), traceId));
    }

    @PatchMapping("/read-all")
    public ResponseEntity<ApiResponse<NotificationReadAllResponse>> markAllRead(
            @CurrentUserId Long userId,
            HttpServletRequest request
    ) {
        String traceId = (String) request.getAttribute(TRACE_ID_ATTRIBUTE);
        int readCount = notificationInboxService.markAllRead(userId);
        NotificationReadAllResponse response = new NotificationReadAllResponse(
                readCount,
                notificationInboxService.getUnreadCount(userId)
        );
        return ResponseEntity.ok(ApiResponse.ok(response, traceId));
    }

    @DeleteMapping("/{notificationId}")
    public ResponseEntity<ApiResponse<Void>> deleteNotification(
            @CurrentUserId Long userId,
            @PathVariable Long notificationId,
            HttpServletRequest request
    ) {
        String traceId = (String) request.getAttribute(TRACE_ID_ATTRIBUTE);
        notificationInboxService.delete(userId, notificationId);
        return ResponseEntity.ok(ApiResponse.ok(traceId));
    }

    @GetMapping("/preferences")
    public ResponseEntity<ApiResponse<NotificationPreferenceResponse>> getPreferences(
            @CurrentUserId Long userId,
            HttpServletRequest request
    ) {
        String traceId = (String) request.getAttribute(TRACE_ID_ATTRIBUTE);
        NotificationPreference preference = notificationPreferenceService.getOrCreate(userId);
        return ResponseEntity.ok(ApiResponse.ok(NotificationPreferenceResponse.from(preference), traceId));
    }

    @PatchMapping("/preferences")
    public ResponseEntity<ApiResponse<NotificationPreferenceResponse>> updatePreferences(
            @CurrentUserId Long userId,
            @Valid @RequestBody NotificationPreferenceUpdateRequest request,
            HttpServletRequest httpRequest
    ) {
        String traceId = (String) httpRequest.getAttribute(TRACE_ID_ATTRIBUTE);
        NotificationPreference preference = notificationPreferenceService.update(
                userId,
                new NotificationPreferenceService.UpdateCommand(
                        request.getPushEnabled(),
                        request.getInAppEnabled(),
                        request.getMatchRequestEnabled(),
                        request.getMatchResultEnabled(),
                        request.getGroupMatchingEnabled(),
                        request.getChatMessageEnabled(),
                        request.getMilestoneEnabled(),
                        request.getReminderEnabled(),
                        request.getQuietHoursEnabled(),
                        request.getQuietHoursStart(),
                        request.getQuietHoursEnd(),
                        request.getTimezone()
                )
        );
        return ResponseEntity.ok(ApiResponse.ok(NotificationPreferenceResponse.from(preference), traceId));
    }

    private NotificationItemResponse toNotificationItem(Notification notification) {
        return NotificationItemResponse.from(notification, parsePayload(notification.getPayloadJson()));
    }

    private Object parsePayload(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(payloadJson, Object.class);
        } catch (Exception e) {
            return payloadJson;
        }
    }
}
