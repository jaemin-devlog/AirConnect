package univ.airconnect.groupmatching.domain;

/**
 * 팀 크기 제약
 * - TWO: 2인 팀
 * - THREE: 3인 팀
 */
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
        throw new IllegalArgumentException("지원하지 않는 팀 인원입니다. size=" + size);
    }
}
