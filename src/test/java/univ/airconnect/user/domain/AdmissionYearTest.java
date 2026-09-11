package univ.airconnect.user.domain;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class AdmissionYearTest {
    @Test
    void onlyAdmissionYearIsPublicRegardlessOfStoredFormat() {
        assertThat(AdmissionYear.from(20240001)).isEqualTo(2024);
        assertThat(AdmissionYear.from(2024)).isEqualTo(2024);
        assertThat(AdmissionYear.from(24)).isEqualTo(2024);
        assertThat(AdmissionYear.from(19981234)).isEqualTo(1998);
        assertThat(AdmissionYear.from(null)).isNull();
        assertThat(AdmissionYear.from(12345678)).isNull();
    }
}
