package univ.airconnect.user.domain;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum MilitaryStatus {
    COMPLETED("군필"),
    NOT_COMPLETED("미필"),
    NOT_APPLICABLE("해당없음");

    private final String description;

    MilitaryStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    @JsonCreator
    public static MilitaryStatus fromString(String value) {
        if (value == null) {
            return null;
        }
        try {
            return MilitaryStatus.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            // 한글 설명으로도 변환 가능하도록
            for (MilitaryStatus status : MilitaryStatus.values()) {
                if (status.description.equals(value)) {
                    return status;
                }
            }
            return null;
        }
    }
}

