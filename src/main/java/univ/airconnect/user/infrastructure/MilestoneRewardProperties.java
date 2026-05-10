package univ.airconnect.user.infrastructure;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.rewards.milestone")
public class MilestoneRewardProperties {

    private int profileImageUploadedTickets = 1;
    private int emailVerifiedTickets = 1;
}
