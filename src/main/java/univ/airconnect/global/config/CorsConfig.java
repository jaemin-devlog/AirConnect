package univ.airconnect.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private static final List<String> DEFAULT_ALLOWED_ORIGIN_PATTERNS = List.of(
            "https://airconnect-admin.web.app"
    );

    @Value("${app.cors.allowed-origin-patterns:}")
    private String allowedOriginPatternsProperty;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        Set<String> allowedOriginPatterns = new LinkedHashSet<>(DEFAULT_ALLOWED_ORIGIN_PATTERNS);
        Arrays.stream(allowedOriginPatternsProperty.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .forEach(allowedOriginPatterns::add);

        if (allowedOriginPatterns.isEmpty()) {
            return;
        }

        registry.addMapping("/**")
                .allowedOriginPatterns(allowedOriginPatterns.toArray(String[]::new))
                .allowedMethods("GET","POST","PUT","PATCH","DELETE","OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}
