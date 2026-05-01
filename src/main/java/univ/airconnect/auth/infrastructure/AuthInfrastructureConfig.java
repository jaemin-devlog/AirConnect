package univ.airconnect.auth.infrastructure;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        ReviewAccountProperties.class,
        AdminAccountProperties.class
})
public class AuthInfrastructureConfig {
}
