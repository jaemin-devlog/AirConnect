package univ.airconnect.global.security.stomp;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.StompWebSocketEndpointRegistration;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;
import univ.airconnect.global.config.WebSocketConfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StompErrorHandlerRegistrationTest {
    @Test
    void endpointRegistryActuallyUsesTheSanitizingErrorHandler() {
        var config = new WebSocketConfig(mock(StompHandler.class),
                mock(StompOutboundAuthorizationInterceptor.class), mock(StompOutboundLoggingInterceptor.class),
                new StompOpsMonitor(20));
        ReflectionTestUtils.setField(config, "allowedOriginPatternsProperty", "");
        var registry = mock(StompEndpointRegistry.class);
        when(registry.addEndpoint(any(String.class))).thenReturn(mock(StompWebSocketEndpointRegistration.class));

        config.registerStompEndpoints(registry);

        var capture = org.mockito.ArgumentCaptor.forClass(StompSubProtocolErrorHandler.class);
        verify(registry).setErrorHandler(capture.capture());
        assertThat(capture.getValue()).isInstanceOf(CustomStompErrorHandler.class);
    }
}
