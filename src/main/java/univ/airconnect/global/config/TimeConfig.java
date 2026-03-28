package univ.airconnect.global.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.TimeZone;

@Configuration
public class TimeConfig {

    /**
     * 전 프로젝트(REST + STOMP + Redis publish/subscribe ObjectMapper)에서 사용할 표준 시간 포맷.
     * - 마이크로초 6자리
     * - 오프셋 필수(XXX)
     */
    public static final DateTimeFormatter OFFSET_DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX");

    /**
     * OffsetDateTime 역직렬화 시 오프셋이 반드시 포함되도록 강제한다.
     * 예) "2026-03-29T12:34:56.123456" (오프셋 없음) -> 예외
     */
    public static class OffsetDateTimeOffsetRequiredDeserializer extends JsonDeserializer<OffsetDateTime> {

        @Override
        public OffsetDateTime deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            String raw = p.getValueAsString();
            if (raw == null || raw.isBlank()) {
                return null;
            }

            // RFC3339/ISO8601 offset indicator: Z or +HH:MM or -HH:MM
            boolean hasOffset = raw.endsWith("Z") || raw.matches(".*[+-]\\d{2}:\\d{2}$");
            if (!hasOffset) {
                throw new IOException("Offset is required for OffsetDateTime: " + raw);
            }

            // 표준 포맷 우선 파싱, 실패 시 ISO_OFFSET_DATE_TIME로 fallback
            try {
                return OffsetDateTime.parse(raw, OFFSET_DATE_TIME_FORMATTER);
            } catch (Exception ignore) {
                return OffsetDateTime.parse(raw, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            }
        }
    }

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer timeObjectMapperCustomizer() {
        return builder -> {
            JavaTimeModule module = new JavaTimeModule();

            // OffsetDateTimeDeserializer는 내부적으로 ISO 포맷을 사용하므로 커스텀 강제 적용
            module.addDeserializer(OffsetDateTime.class, new OffsetDateTimeOffsetRequiredDeserializer());

            builder.modules(module);
            builder.featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            builder.timeZone(TimeZone.getTimeZone("UTC"));
        };
    }

    /**
     * 프로젝트 전반에서 주입받아 사용할 수 있는 표준 ObjectMapper.
     * (Spring이 관리하는 기본 ObjectMapper가 위 Customizer를 통해 동일 설정을 가지지만,
     *  명시적으로 주입받는 곳에서 "표준"임을 표현하기 위해 제공)
     */
    @Bean
    public DateTimeFormatter airconnectOffsetDateTimeFormatter() {
        return OFFSET_DATE_TIME_FORMATTER;
    }
}



