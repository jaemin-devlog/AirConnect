package univ.airconnect.moderation.infrastructure;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "moderation")
public class ModerationProperties {

    private Report report = new Report();
    private Support support = new Support();

    @Getter
    @Setter
    public static class Report {
        private long duplicateWindowMinutes = 60L;
    }

    @Getter
    @Setter
    public static class Support {
        private String email;
        private String url;
        private String contactText;
    }
}
