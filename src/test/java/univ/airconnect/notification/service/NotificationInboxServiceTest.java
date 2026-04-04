package univ.airconnect.notification.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import univ.airconnect.notification.domain.NotificationType;
import univ.airconnect.notification.domain.entity.Notification;
import univ.airconnect.notification.repository.NotificationRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationInboxServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Test
    void getNotifications_returnsCursorPageWithUnreadCount() {
        NotificationInboxService service = new NotificationInboxService(notificationRepository);
        Notification first = notification(30L, 1L, NotificationType.MATCH_REQUEST_RECEIVED);
        Notification second = notification(20L, 1L, NotificationType.TEAM_MEMBER_JOINED);
        Notification third = notification(10L, 1L, NotificationType.TEAM_MEMBER_LEFT);

        when(notificationRepository.findByUserIdAndTypeNotAndDeletedAtIsNullOrderByIdDesc(
                eq(1L), eq(NotificationType.CHAT_MESSAGE_RECEIVED), any()))
                .thenReturn(List.of(first, second, third));
        when(notificationRepository.countByUserIdAndTypeNotAndReadAtIsNullAndDeletedAtIsNull(
                1L, NotificationType.CHAT_MESSAGE_RECEIVED))
                .thenReturn(7L);

        NotificationInboxService.NotificationSlice response =
                service.getNotifications(1L, null, 2, false, null);

        assertThat(response.items()).containsExactly(first, second);
        assertThat(response.unreadCount()).isEqualTo(7L);
        assertThat(response.hasNext()).isTrue();
        assertThat(response.nextCursorId()).isEqualTo(20L);
        assertThat(response.requestedSize()).isEqualTo(2);
    }

    @Test
    void markRead_markAllRead_andDelete_updateInboxState() {
        NotificationInboxService service = new NotificationInboxService(notificationRepository);
        Notification notification = notification(101L, 9L, NotificationType.MATCH_REQUEST_ACCEPTED);

        when(notificationRepository.findByIdAndUserIdAndDeletedAtIsNull(101L, 9L))
                .thenReturn(Optional.of(notification));
        when(notificationRepository.markAllReadExcludingType(
                eq(9L), eq(NotificationType.CHAT_MESSAGE_RECEIVED), any()))
                .thenReturn(4);

        Notification read = service.markRead(9L, 101L);
        int markedCount = service.markAllRead(9L);
        service.delete(9L, 101L);

        assertThat(read.isRead()).isTrue();
        assertThat(markedCount).isEqualTo(4);
        assertThat(notification.isDeleted()).isTrue();
        verify(notificationRepository).markAllReadExcludingType(eq(9L), eq(NotificationType.CHAT_MESSAGE_RECEIVED), any());
    }

    private Notification notification(Long id, Long userId, NotificationType type) {
        Notification notification = Notification.create(
                userId,
                type,
                "title-" + id,
                "body-" + id,
                "airconnect://notification/" + id,
                99L,
                null,
                "{\"id\":" + id + "}",
                type.requiresDedupeKey() ? "dedupe-" + id : null
        );
        ReflectionTestUtils.setField(notification, "id", id);
        return notification;
    }
}
