package univ.airconnect.groupmatching.domain;

/**
 * 임시 팀방 상태
 *
 * OPEN         : 임시방 생성 / 모집 중
 * READY_CHECK  : 정원 충족 후 준비 상태 확인 중
 * QUEUE_WAITING: 서버 매칭 큐 대기 중
 * MATCHED      : 상대 팀 매칭 완료, 최종 그룹방 생성 직전/직후
 * CLOSED       : 최종 그룹방으로 전환 완료
 * CANCELLED    : 방 취소
 */
public enum GTemporaryTeamRoomStatus {
    OPEN,
    READY_CHECK,
    QUEUE_WAITING,
    MATCHED,
    CLOSED,
    CANCELLED;

    public boolean canModifyMembers() {
        return this == OPEN || this == READY_CHECK;
    }

    public boolean canEnterQueue() {
        return this == READY_CHECK;
    }

    public boolean isQueueing() {
        return this == QUEUE_WAITING;
    }

    public boolean isTerminal() {
        return this == CLOSED || this == CANCELLED;
    }
}
