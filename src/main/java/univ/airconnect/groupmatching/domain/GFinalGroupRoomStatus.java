package univ.airconnect.groupmatching.domain;

/**
 * 최종 그룹 채팅방 상태
 * - ACTIVE: 생성됨, 대화 가능
 * - ENDED: 정상 종료
 * - CANCELLED: 예외 종료
 */
public enum GFinalGroupRoomStatus {
    ACTIVE,
    ENDED,
    CANCELLED;
}
