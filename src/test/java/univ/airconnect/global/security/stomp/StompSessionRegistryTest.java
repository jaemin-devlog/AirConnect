package univ.airconnect.global.security.stomp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class StompSessionRegistryTest {

    private StompSessionRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new StompSessionRegistry();
    }

    @Test
    void countsDistinctUsersAcrossMultipleSessions() {
        registry.register("user1-phone", 1L);
        assertThat(registry.onlineUserCount()).isEqualTo(1);
        registry.register("user1-tablet", 1L);
        assertThat(registry.onlineUserCount()).isEqualTo(1);

        registry.register("user2-phone", 2L);
        assertThat(registry.onlineUserCount()).isEqualTo(2);

        registry.remove("user1-phone");
        assertThat(registry.onlineUserCount()).isEqualTo(2);

        registry.remove("user1-tablet");
        assertThat(registry.onlineUserCount()).isEqualTo(1);
    }

    @Test
    void revokeUserRemovesAllSessions() {
        registry.register("user1-phone", 1L);
        registry.register("user1-tablet", 1L);
        registry.register("user2-phone", 2L);
        assertThat(registry.revokeUser(1L)).isEqualTo(2);

        assertThat(registry.onlineUserCount()).isEqualTo(1);
    }
}
