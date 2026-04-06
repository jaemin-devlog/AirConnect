package univ.airconnect.auth.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TokenHashServiceTest {

    @Test
    void hashAndMatches_workWithoutPlaintextStorage() {
        TokenHashService tokenHashService = new TokenHashService("pepper-value");

        String hash = tokenHashService.hash("refresh-token-raw");

        assertThat(hash).isNotBlank();
        assertThat(hash).isNotEqualTo("refresh-token-raw");
        assertThat(tokenHashService.matches("refresh-token-raw", hash)).isTrue();
        assertThat(tokenHashService.matches("other-token", hash)).isFalse();
    }
}
