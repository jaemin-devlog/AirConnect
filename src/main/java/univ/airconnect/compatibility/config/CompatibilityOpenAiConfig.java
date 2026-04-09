package univ.airconnect.compatibility.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(OpenAiCompatibilityProperties.class)
public class CompatibilityOpenAiConfig {

    @Bean
    @Qualifier("compatibilityOpenAiRestClient")
    public RestClient compatibilityOpenAiRestClient(OpenAiCompatibilityProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.resolvedConnectTimeout());
        requestFactory.setReadTimeout(properties.resolvedReadTimeout());

        return RestClient.builder()
                .baseUrl(properties.resolvedBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }
}
