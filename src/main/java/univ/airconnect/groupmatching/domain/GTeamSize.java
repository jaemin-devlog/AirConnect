package univ.airconnect.groupmatching.domain;

import univ.airconnect.global.error.BusinessException;
import univ.airconnect.global.error.ErrorCode;

public enum GTeamSize {
    TWO(2),
    THREE(3);

    private final int value;

    GTeamSize(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static GTeamSize from(int size) {
        for (GTeamSize ts : values()) {
            if (ts.value == size) {
                return ts;
            }
        }
        throw new BusinessException(
                ErrorCode.INVALID_TEAM_SIZE,
                "지원하지 않는 팀 인원입니다. size=" + size
        );
    }
}
