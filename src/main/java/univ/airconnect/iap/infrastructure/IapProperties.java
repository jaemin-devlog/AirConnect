package univ.airconnect.iap.infrastructure;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "iap")
public class IapProperties {

    private Apple apple = new Apple();
    private Google google = new Google();

    @Getter
    @Setter
    public static class Apple {
        private String bundleId;
        private String environment = "PRODUCTION";
    }

    @Getter
    @Setter
    public static class Google {
        private String packageName;
        private String serviceAccountJsonPath;
        private boolean verifyEnabled = false;
    }
}

