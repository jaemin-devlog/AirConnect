package univ.airconnect.matching.service;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import univ.airconnect.notification.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import static org.mockito.Mockito.mock;

@Configuration
@Profile("test")
public class TestNotificationConfig {

    /**
     * DataJpaTest 슬라이스에서 MatchingService가 NotificationService를 의존할 때
     * 전체 notification 컨텍스트를 끌어오지 않기 위한 테스트 전용 목 빈.
     */
    @Bean
    @Primary
    public NotificationService notificationService() {
        return mock(NotificationService.class);
    }

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}


