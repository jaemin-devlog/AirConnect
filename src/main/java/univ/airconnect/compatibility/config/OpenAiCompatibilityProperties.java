package univ.airconnect.compatibility.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "openai.compatibility")
public record OpenAiCompatibilityProperties(
        boolean enabled,
        String apiKey,
        String baseUrl,
        String model,
        Duration connectTimeout,
        Duration readTimeout
) {

    public String resolvedBaseUrl() {
        return isBlank(baseUrl) ? "https://api.openai.com/v1" : baseUrl;
    }

    public String resolvedModel() {
        return isBlank(model) ? "gpt-5-nano" : model;
    }

    public Duration resolvedConnectTimeout() {
        return connectTimeout != null ? connectTimeout : Duration.ofSeconds(2);
    }

    public Duration resolvedReadTimeout() {
        return readTimeout != null ? readTimeout : Duration.ofSeconds(5);
    }

    public boolean hasApiKey() {
        return !isBlank(apiKey);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
