package univ.airconnect.groupmatching.domain;

/**
 * 매칭 결과 상태
 *
 * MATCHED             : 두 팀 매칭 성사
 * FINAL_ROOM_CREATED  : 최종 그룹 채팅방 생성 완료
 * CANCELLED           : 매칭 후속 처리 중 취소
 */
public enum GMatchResultStatus {
    MATCHED,
    FINAL_ROOM_CREATED,
    CANCELLED;

    public boolean isTerminal() {
        return this == FINAL_ROOM_CREATED || this == CANCELLED;
    }
}
