package univ.airconnect.groupmatching.domain;

/**
 * 상대 팀 성별 필터
 * - M: 남성 팀만 허용
 * - F: 여성 팀만 허용
 * - ANY: 모두 허용
 */
public enum GGenderFilter {
    M,
    F,
    ANY;

    public boolean allows(GTeamGender targetGender) {
        if (this == ANY) {
            return true;
        }
        return this.name().equals(targetGender.name());
    }
}
