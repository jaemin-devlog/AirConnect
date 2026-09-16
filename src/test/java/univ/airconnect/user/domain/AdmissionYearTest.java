package univ.airconnect.user.domain;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class AdmissionYearTest {
    @Test
    void onlyAdmissionYearIsPublicRegardlessOfStoredFormat() {
        assertThat(AdmissionYear.from(20240001)).isEqualTo(24);
        assertThat(AdmissionYear.from(202100048)).isEqualTo(21);
        assertThat(AdmissionYear.from(2024)).isEqualTo(24);
        assertThat(AdmissionYear.from(24)).isEqualTo(24);
        assertThat(AdmissionYear.from(19981234)).isEqualTo(98);
        assertThat(AdmissionYear.from(null)).isNull();
        assertThat(AdmissionYear.from(12345678)).isNull();
    }

    @Test
    void recognizesOnlyTwoDigitValuesAsCanonicalInput() {
        assertThat(AdmissionYear.isCanonical(21)).isTrue();
        assertThat(AdmissionYear.isCanonical(2021)).isFalse();
        assertThat(AdmissionYear.isCanonical(202100048)).isFalse();
        assertThat(AdmissionYear.isCanonical(null)).isFalse();
    }
}
