package univ.airconnect.notification.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import univ.airconnect.global.error.BusinessException;
import univ.airconnect.global.error.ErrorCode;
import univ.airconnect.notification.domain.NotificationType;
import univ.airconnect.notification.domain.entity.Notification;
import univ.airconnect.notification.repository.NotificationRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 앱 내 알림함 관련 API 로직을 처리한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationInboxService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final NotificationRepository notificationRepository;

    /**
     * 사용자 기준 커서 기반 알림 페이지 한 개를 반환한다.
     */
    public NotificationSlice getNotifications(Long userId, Long cursorId, Integer size, Boolean unreadOnly, NotificationType type) {
        int pageSize = normalizePageSize(size);
        Pageable pageable = PageRequest.of(0, pageSize + 1);
        boolean unread = Boolean.TRUE.equals(unreadOnly);

        List<Notification> fetched = fetchNotifications(userId, cursorId, unread, type, pageable);
        boolean hasNext = fetched.size() > pageSize;
        List<Notification> items = hasNext
                ? new ArrayList<>(fetched.subList(0, pageSize))
                : new ArrayList<>(fetched);
        Long nextCursorId = hasNext && !items.isEmpty() ? items.get(items.size() - 1).getId() : null;

        return new NotificationSlice(
                items,
                notificationRepository.countByUserIdAndReadAtIsNullAndDeletedAtIsNull(userId),
                hasNext,
                nextCursorId,
                pageSize
        );
    }

    /**
     * 사용자의 미읽음 알림 개수를 반환한다.
     */
    public long getUnreadCount(Long userId) {
        return notificationRepository.countByUserIdAndReadAtIsNullAndDeletedAtIsNull(userId);
    }

    /**
     * 알림 한 건을 읽음 처리한다.
     */
    @Transactional
    public Notification markRead(Long userId, Long notificationId) {
        Notification notification = getRequiredNotification(userId, notificationId);
        notification.markRead();
        return notification;
    }

    /**
     * 모든 미읽음 알림을 읽음 처리하고 반영된 개수를 반환한다.
     */
    @Transactional
    public int markAllRead(Long userId) {
        return notificationRepository.markAllRead(userId, LocalDateTime.now());
    }

    /**
     * 사용자의 알림함에서 알림 한 건을 소프트 삭제한다.
     */
    @Transactional
    public void delete(Long userId, Long notificationId) {
        Notification notification = getRequiredNotification(userId, notificationId);
        notification.softDelete();
    }

    private List<Notification> fetchNotifications(Long userId,
                                                  Long cursorId,
                                                  boolean unreadOnly,
                                                  NotificationType type,
                                                  Pageable pageable) {
        if (cursorId == null) {
            if (type != null && unreadOnly) {
                return notificationRepository.findByUserIdAndTypeAndReadAtIsNullAndDeletedAtIsNullOrderByIdDesc(
                        userId,
                        type,
                        pageable
                );
            }
            if (type != null) {
                return notificationRepository.findByUserIdAndTypeAndDeletedAtIsNullOrderByIdDesc(
                        userId,
                        type,
                        pageable
                );
            }
            if (unreadOnly) {
                return notificationRepository.findByUserIdAndReadAtIsNullAndDeletedAtIsNullOrderByIdDesc(
                        userId,
                        pageable
                );
            }
            return notificationRepository.findByUserIdAndDeletedAtIsNullOrderByIdDesc(userId, pageable);
        }

        if (type != null && unreadOnly) {
            return notificationRepository.findByUserIdAndTypeAndReadAtIsNullAndDeletedAtIsNullAndIdLessThanOrderByIdDesc(
                    userId,
                    type,
                    cursorId,
                    pageable
            );
        }
        if (type != null) {
            return notificationRepository.findByUserIdAndTypeAndDeletedAtIsNullAndIdLessThanOrderByIdDesc(
                    userId,
                    type,
                    cursorId,
                    pageable
            );
        }
        if (unreadOnly) {
            return notificationRepository.findByUserIdAndReadAtIsNullAndDeletedAtIsNullAndIdLessThanOrderByIdDesc(
                    userId,
                    cursorId,
                    pageable
            );
        }
        return notificationRepository.findByUserIdAndDeletedAtIsNullAndIdLessThanOrderByIdDesc(
                userId,
                cursorId,
                pageable
        );
    }

    private Notification getRequiredNotification(Long userId, Long notificationId) {
        return notificationRepository.findByIdAndUserIdAndDeletedAtIsNull(notificationId, userId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.NOT_FOUND,
                        "알림을 찾을 수 없습니다."
                ));
    }

    private int normalizePageSize(Integer requestedSize) {
        if (requestedSize == null) {
            return DEFAULT_PAGE_SIZE;
        }
        if (requestedSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(requestedSize, MAX_PAGE_SIZE);
    }

    /**
     * 알림함용 커서 페이지 결과 모델이다.
     */
    public record NotificationSlice(
            List<Notification> items,
            long unreadCount,
            boolean hasNext,
            Long nextCursorId,
            int requestedSize
    ) {
    }
}
