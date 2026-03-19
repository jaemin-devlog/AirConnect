package univ.airconnect.groupmatching.domain;

/**
 * 사용자 매칭 플로우 상태
 * - IDLE: 기본 상태
 * - IN_TEMP_TEAM_ROOM: 임시 팀방 참여 중
 * - IN_QUEUE: 팀 단위 큐 대기 중
 * - IN_FINAL_GROUP: 최종 그룹 채팅방 입장 완료
 */
public enum GUserMatchingStatus {
    IDLE,
    IN_TEMP_TEAM_ROOM,
    IN_QUEUE,
    IN_FINAL_GROUP;

    public boolean isAvailable() {
        return this == IDLE;
    }
}
