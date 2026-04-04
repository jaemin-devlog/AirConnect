package univ.airconnect.global.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;
import univ.airconnect.global.security.stomp.CustomStompErrorHandler;
import univ.airconnect.global.security.stomp.StompOpsMonitor;
import univ.airconnect.global.security.stomp.StompOutboundLoggingInterceptor;
import univ.airconnect.global.security.stomp.StompHandler;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompHandler stompHandler;
    private final StompOutboundLoggingInterceptor stompOutboundLoggingInterceptor;

    @Value("${app.websocket.inbound.core-pool-size:16}")
    private int inboundCorePoolSize;

    @Value("${app.websocket.inbound.max-pool-size:64}")
    private int inboundMaxPoolSize;

    @Value("${app.websocket.inbound.queue-capacity:5000}")
    private int inboundQueueCapacity;

    @Value("${app.websocket.inbound.keep-alive-seconds:60}")
    private int inboundKeepAliveSeconds;

    @Value("${app.websocket.outbound.core-pool-size:16}")
    private int outboundCorePoolSize;

    @Value("${app.websocket.outbound.max-pool-size:64}")
    private int outboundMaxPoolSize;

    @Value("${app.websocket.outbound.queue-capacity:5000}")
    private int outboundQueueCapacity;

    @Value("${app.websocket.outbound.keep-alive-seconds:60}")
    private int outboundKeepAliveSeconds;

    @Value("${app.websocket.transport.send-time-limit-ms:15000}")
    private int sendTimeLimitMs;

    @Value("${app.websocket.transport.send-buffer-size-limit-bytes:1048576}")
    private int sendBufferSizeLimitBytes;

    @Value("${app.websocket.transport.message-size-limit-bytes:131072}")
    private int messageSizeLimitBytes;

    @Value("${app.websocket.allowed-origin-patterns:}")
    private String allowedOriginPatternsProperty;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // 구독 prefix
        config.enableSimpleBroker("/sub");
        // 발행 prefix
        config.setApplicationDestinationPrefixes("/pub");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 네이티브 앱 및 일반 WebSocket/STOMP 클라이언트용
        List<String> allowedOriginPatterns = resolveAllowedOriginPatterns();

        var stompEndpoint = registry.addEndpoint("/ws-stomp");
        if (!allowedOriginPatterns.isEmpty()) {
            stompEndpoint.setAllowedOriginPatterns(allowedOriginPatterns.toArray(String[]::new));
        }

        // 브라우저 SockJS가 꼭 필요한 경우에만 유지
        var sockJsEndpoint = registry.addEndpoint("/ws-stomp-sockjs");
        if (!allowedOriginPatterns.isEmpty()) {
            sockJsEndpoint.setAllowedOriginPatterns(allowedOriginPatterns.toArray(String[]::new));
        }
        sockJsEndpoint.withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.taskExecutor()
                .corePoolSize(inboundCorePoolSize)
                .maxPoolSize(inboundMaxPoolSize)
                .queueCapacity(inboundQueueCapacity)
                .keepAliveSeconds(inboundKeepAliveSeconds);
        registration.interceptors(stompHandler);
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.taskExecutor()
                .corePoolSize(outboundCorePoolSize)
                .maxPoolSize(outboundMaxPoolSize)
                .queueCapacity(outboundQueueCapacity)
                .keepAliveSeconds(outboundKeepAliveSeconds);
        registration.interceptors(stompOutboundLoggingInterceptor);
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registry) {
        registry.setSendTimeLimit(sendTimeLimitMs);
        registry.setSendBufferSizeLimit(sendBufferSizeLimitBytes);
        registry.setMessageSizeLimit(messageSizeLimitBytes);
    }

    @Bean("stompSubProtocolErrorHandler")
    public StompSubProtocolErrorHandler stompSubProtocolErrorHandler(StompOpsMonitor stompOpsMonitor) {
        return new CustomStompErrorHandler(stompOpsMonitor);
    }

    private List<String> resolveAllowedOriginPatterns() {
        return Arrays.stream(allowedOriginPatternsProperty.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
    }
}
