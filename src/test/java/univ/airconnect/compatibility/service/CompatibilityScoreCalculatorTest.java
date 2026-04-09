package univ.airconnect.compatibility.service;

import org.junit.jupiter.api.Test;
import univ.airconnect.compatibility.domain.CompatibilityGrade;
import univ.airconnect.compatibility.domain.CompatibilityProfile;
import univ.airconnect.compatibility.domain.CompatibilityResult;
import univ.airconnect.compatibility.domain.MbtiCompatibilityTier;

import static org.assertj.core.api.Assertions.assertThat;

class CompatibilityScoreCalculatorTest {

    private final CompatibilityScoreCalculator calculator = new CompatibilityScoreCalculator();

    @Test
    void calculate_returnsAmazing_whenMostSignalsMatchAndMbtiIsIdeal() {
        CompatibilityProfile me = profile(
                1L,
                22,
                "컴퓨터공학과",
                20240101,
                170,
                "INTJ",
                "NO",
                "NONE",
                "서울 강남구"
        );
        CompatibilityProfile target = profile(
                2L,
                23,
                "컴퓨터공학과",
                20240155,
                176,
                "ENFP",
                "NO",
                "NONE",
                "서울 송파구"
        );

        CompatibilityResult result = calculator.calculate(me, target);

        assertThat(result.getScore()).isEqualTo(100);
        assertThat(result.getGrade()).isEqualTo(CompatibilityGrade.AMAZING);
        assertThat(result.getMbtiTier()).isEqualTo(MbtiCompatibilityTier.IDEAL);
        assertThat(result.getReasons()).hasSize(3);
        assertThat(result.getCautions()).hasSize(2);
    }

    @Test
    void calculate_usesBadMbtiTierAndLowGrade_whenManySignalsDiffer() {
        CompatibilityProfile me = profile(
                1L,
                20,
                "컴퓨터공학과",
                20240101,
                160,
                "INTJ",
                "NO",
                "NONE",
                "서울 강남구"
        );
        CompatibilityProfile target = profile(
                2L,
                34,
                "무용학과",
                20160101,
                195,
                "ESFP",
                "YES",
                "CHRISTIAN",
                "부산 해운대구"
        );

        CompatibilityResult result = calculator.calculate(me, target);

        assertThat(result.getMbtiTier()).isEqualTo(MbtiCompatibilityTier.BAD);
        assertThat(result.getGrade()).isEqualTo(CompatibilityGrade.LOW);
        assertThat(result.getScore()).isLessThan(50);
    }

    private CompatibilityProfile profile(
            Long userId,
            Integer age,
            String deptName,
            Integer studentNum,
            Integer height,
            String mbti,
            String smoking,
            String religion,
            String residence
    ) {
        return new CompatibilityProfile(
                userId,
                age,
                deptName,
                studentNum,
                height,
                mbti,
                smoking,
                religion,
                residence
        );
    }
}
