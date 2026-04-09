package univ.airconnect.compatibility.domain;

public enum CompatibilityFactor {
    AGE("나이"),
    DEPARTMENT("학과"),
    STUDENT_NUMBER("학번"),
    HEIGHT("키"),
    MBTI("MBTI"),
    SMOKING("흡연 여부"),
    RELIGION("종교"),
    RESIDENCE("본가 거주지");

    private final String label;

    CompatibilityFactor(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
