package univ.airconnect.compatibility.domain;

import java.util.Map;
import java.util.Set;

public final class MbtiCompatibilityTable {

    private static final Map<String, Set<String>> IDEAL = Map.ofEntries(
            Map.entry("INFP", Set.of("ENFJ", "ENTJ")),
            Map.entry("ENFP", Set.of("INFJ", "INTJ")),
            Map.entry("INFJ", Set.of("ENFP", "ENTP")),
            Map.entry("ENFJ", Set.of("INFP", "ISFP")),
            Map.entry("INTJ", Set.of("ENFP", "ENTP")),
            Map.entry("ENTJ", Set.of("INFP", "INTP")),
            Map.entry("INTP", Set.of("ENTJ", "ESTJ")),
            Map.entry("ENTP", Set.of("INFJ", "INTJ")),
            Map.entry("ISFP", Set.of("ENFJ", "ESFJ", "ESTJ")),
            Map.entry("ESFP", Set.of("ISFJ", "ISTJ")),
            Map.entry("ISTP", Set.of("ESFJ", "ESTJ")),
            Map.entry("ESTP", Set.of("ISFJ", "ISTJ")),
            Map.entry("ISFJ", Set.of("ESFP", "ESTP")),
            Map.entry("ESFJ", Set.of("ISFP", "ISTP")),
            Map.entry("ISTJ", Set.of("ESFP", "ESTP")),
            Map.entry("ESTJ", Set.of("INTP", "ISFP", "ISTP"))
    );

    private static final Map<String, Set<String>> BAD = Map.ofEntries(
            Map.entry("INFP", Set.of("ISFP", "ESFP", "ISTP", "ESTP", "ISFJ", "ESFJ", "ISTJ", "ESTJ")),
            Map.entry("ENFP", Set.of("ISFP", "ESFP", "ISTP", "ESTP", "ISFJ", "ESFJ", "ISTJ", "ESTJ")),
            Map.entry("INFJ", Set.of("ISFP", "ESFP", "ISTP", "ESTP", "ISFJ", "ESFJ", "ISTJ", "ESTJ")),
            Map.entry("ENFJ", Set.of("ESFP", "ISTP", "ESTP", "ISFJ", "ESFJ", "ISTJ", "ESTJ")),
            Map.entry("INTJ", Set.of("ISFP", "ESFP", "ISTP", "ESTP", "ISFJ", "ESFJ", "ISTJ", "ESTJ")),
            Map.entry("ENTJ", Set.of("ESFP", "ESTP", "ISFJ", "ESFJ", "ISTJ")),
            Map.entry("INTP", Set.of("ISFP", "ESFP", "ISTP", "ESTP", "ISFJ", "ESFJ", "ISTJ")),
            Map.entry("ENTP", Set.of("ISFP", "ESFP", "ISTP", "ESTP", "ISFJ", "ESFJ", "ISTJ", "ESTJ")),
            Map.entry("ISFP", Set.of("INFP", "ENFP", "INFJ", "INTJ", "INTP", "ENTP")),
            Map.entry("ESFP", Set.of("INFP", "ENFP", "INFJ", "ENFJ", "INTJ", "ENTJ", "INTP", "ENTP")),
            Map.entry("ISTP", Set.of("INFP", "ENFP", "INFJ", "ENFJ", "INTJ", "INTP", "ENTP")),
            Map.entry("ESTP", Set.of("INFP", "ENFP", "INFJ", "ENFJ", "INTJ", "ENTJ", "INTP", "ENTP")),
            Map.entry("ISFJ", Set.of("INFP", "ENFP", "INFJ", "ENFJ", "INTJ", "ENTJ", "INTP", "ENTP")),
            Map.entry("ESFJ", Set.of("INFP", "ENFP", "INFJ", "ENFJ", "INTJ", "ENTJ", "INTP", "ENTP")),
            Map.entry("ISTJ", Set.of("INFP", "ENFP", "INFJ", "ENFJ", "INTJ", "ENTJ", "INTP", "ENTP")),
            Map.entry("ESTJ", Set.of("INFP", "ENFP", "INFJ", "ENFJ", "INTJ", "ENTP"))
    );

    private MbtiCompatibilityTable() {
    }

    public static MbtiCompatibilityTier tier(String myMbti, String targetMbti) {
        String my = normalize(myMbti);
        String target = normalize(targetMbti);

        if (IDEAL.getOrDefault(my, Set.of()).contains(target)) {
            return MbtiCompatibilityTier.IDEAL;
        }
        if (BAD.getOrDefault(my, Set.of()).contains(target)) {
            return MbtiCompatibilityTier.BAD;
        }
        return MbtiCompatibilityTier.POTENTIAL;
    }

    private static String normalize(String mbti) {
        return mbti == null ? "" : mbti.trim().toUpperCase();
    }
}
