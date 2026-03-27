package univ.airconnect.notification.domain;

/**
 * 알림의 실제 비즈니스 이벤트 타입이다.
 * 앱 deep link, 문구 템플릿, 사용자 설정 키 분기 기준으로 사용된다.
 */
public enum NotificationType {
    /** 상대가 1:1 매칭 요청을 보냈을 때 */
    MATCH_REQUEST_RECEIVED(NotificationCategory.MATCHING, true),
    /** 내가 보낸 1:1 매칭 요청이 수락됐을 때 */
    MATCH_REQUEST_ACCEPTED(NotificationCategory.MATCHING, true),
    /** 내가 보낸 1:1 매칭 요청이 거절됐을 때 */
    MATCH_REQUEST_REJECTED(NotificationCategory.MATCHING, true),
    /** 그룹 매칭이 성사되어 최종 채팅방이 생성됐을 때 */
    GROUP_MATCHED(NotificationCategory.GROUP_MATCHING, true),
    /** 채팅방에 새 메시지가 도착했을 때 */
    CHAT_MESSAGE_RECEIVED(NotificationCategory.CHAT, true),
    /** 마일스톤 달성과 함께 보상이 지급됐을 때 */
    MILESTONE_REWARDED(NotificationCategory.MILESTONE, true),
    /** 팀원이 모두 모여 준비 체크가 필요할 때 */
    TEAM_READY_REQUIRED(NotificationCategory.GROUP_MATCHING, true),
    /** 전원이 ready 상태가 되어 방장이 매칭을 시작할 수 있을 때 */
    TEAM_ALL_READY(NotificationCategory.GROUP_MATCHING, true),
    /** 팀 방이 해산됐을 때 */
    TEAM_ROOM_CANCELLED(NotificationCategory.GROUP_MATCHING, true),
    /** 새로운 팀원이 팀에 들어왔을 때 */
    TEAM_MEMBER_JOINED(NotificationCategory.GROUP_MATCHING, false),
    /** 팀원이 팀에서 나갔을 때 */
    TEAM_MEMBER_LEFT(NotificationCategory.GROUP_MATCHING, false),
    /** 약속 1시간 전 리마인드 */
    APPOINTMENT_REMINDER_1H(NotificationCategory.REMINDER, true),
    /** 약속 10분 전 리마인드 */
    APPOINTMENT_REMINDER_10M(NotificationCategory.REMINDER, true),
    /** 운영 공지/시스템 알림 */
    SYSTEM_ANNOUNCEMENT(NotificationCategory.SYSTEM, false);

    private final NotificationCategory category;
    private final boolean dedupeKeyRequired;

    NotificationType(NotificationCategory category, boolean dedupeKeyRequired) {
        this.category = category;
        this.dedupeKeyRequired = dedupeKeyRequired;
    }

    /** 타입별 고정 카테고리를 반환한다. */
    public NotificationCategory getCategory() {
        return category;
    }

    /** 이 타입이 중복 방지 키를 필수로 요구하는지 반환한다. */
    public boolean requiresDedupeKey() {
        return dedupeKeyRequired;
    }
}
