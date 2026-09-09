package univ.airconnect.ticket.service;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FestivalCouponCatalogTest {

    @Test
    void catalogContainsExactlyFiveHundredUniqueSixDigitCodes() throws Exception {
        InputStream input = getClass().getClassLoader()
                .getResourceAsStream("festival/coupons-500.txt");
        assertThat(input).isNotNull();

        List<String> codes;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                input, StandardCharsets.UTF_8))) {
            codes = reader.lines().filter(line -> !line.isBlank()).toList();
        }

        assertThat(codes).hasSize(500);
        assertThat(new HashSet<>(codes)).hasSize(500);
        assertThat(codes).allMatch(code -> code.matches("\\d{6}"));
        assertThat(codes).contains("049893", "000599", "002093");
    }
}
