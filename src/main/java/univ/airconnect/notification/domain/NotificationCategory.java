package univ.airconnect.notification.domain;

/**
 * 알림을 기능 영역별로 분류하기 위한 카테고리다.
 * 사용자 설정, 목록 필터링, 알림 채널 분리에 사용된다.
 */
public enum NotificationCategory {
    /** 1:1 매칭 관련 알림 */
    MATCHING,
    /** 그룹 매칭 및 팀 구성 관련 알림 */
    GROUP_MATCHING,
    /** 채팅 메시지 관련 알림 */
    CHAT,
    /** 마일스톤/보상 지급 관련 알림 */
    MILESTONE,
    /** 일정 리마인드 관련 알림 */
    REMINDER,
    /** 공지/운영 알림 */
    SYSTEM
}
