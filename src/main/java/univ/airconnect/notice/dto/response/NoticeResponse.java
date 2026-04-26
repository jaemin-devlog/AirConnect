package univ.airconnect.notice.dto.response;

import univ.airconnect.admin.AdminNotice;

import java.time.LocalDateTime;

public record NoticeResponse(
        Long noticeId,
        String title,
        String body,
        String deeplink,
        boolean activeUsersOnly,
        int recipientCount,
        Long createdByUserId,
        LocalDateTime createdAt
) {
    public static NoticeResponse from(AdminNotice notice) {
        return new NoticeResponse(
                notice.getId(),
                notice.getTitle(),
                notice.getBody(),
                notice.getDeeplink(),
                notice.isActiveUsersOnly(),
                notice.getRecipientCount(),
                notice.getCreatedByUserId(),
                notice.getCreatedAt()
        );
    }
}
