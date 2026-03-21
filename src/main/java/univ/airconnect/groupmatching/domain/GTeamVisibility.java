package univ.airconnect.groupmatching.domain;

/**
 * 임시 팀방 공개 범위
 * - PUBLIC: 공개 모집 가능
 * - PRIVATE: 초대 코드 기반 입장
 */
public enum GTeamVisibility {
    PUBLIC,
    PRIVATE;

    public boolean isPrivate() {
        return this == PRIVATE;
    }
}
