package univ.airconnect.global.security.stomp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StompSessionRegistryTest {

    @Mock ApplicationEventPublisher eventPublisher;

    private StompSessionRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new StompSessionRegistry();
        registry.setApplicationEventPublisher(eventPublisher);
    }

    @Test
    void countsDistinctUsersAndPublishesOnlyWhenUserCountChanges() {
        registry.register("user1-phone", 1L);
        assertPublishedCount(1);

        clearInvocations(eventPublisher);
        registry.register("user1-tablet", 1L);
        assertThat(registry.onlineUserCount()).isEqualTo(1);
        verify(eventPublisher, never()).publishEvent(org.mockito.ArgumentMatchers.any());

        registry.register("user2-phone", 2L);
        assertPublishedCount(2);

        clearInvocations(eventPublisher);
        registry.remove("user1-phone");
        assertThat(registry.onlineUserCount()).isEqualTo(2);
        verify(eventPublisher, never()).publishEvent(org.mockito.ArgumentMatchers.any());

        registry.remove("user1-tablet");
        assertPublishedCount(1);
    }

    @Test
    void revokeUserRemovesAllSessionsAndPublishesOneUpdate() {
        registry.register("user1-phone", 1L);
        registry.register("user1-tablet", 1L);
        registry.register("user2-phone", 2L);
        clearInvocations(eventPublisher);

        assertThat(registry.revokeUser(1L)).isEqualTo(2);

        assertThat(registry.onlineUserCount()).isEqualTo(1);
        assertPublishedCount(1);
    }

    private void assertPublishedCount(int expected) {
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(payload.capture());
        assertThat(payload.getValue()).isInstanceOf(OnlineUserCountChangedEvent.class);
        assertThat(((OnlineUserCountChangedEvent) payload.getValue()).onlineUserCount()).isEqualTo(expected);
        assertThat(registry.onlineUserCount()).isEqualTo(expected);
        clearInvocations(eventPublisher);
    }
}
