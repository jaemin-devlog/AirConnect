package univ.airconnect.user.infrastructure;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ProfileImageProperties.class)
public class UserInfrastructureConfig {
}
