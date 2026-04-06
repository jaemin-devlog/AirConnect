package univ.airconnect.iap.infrastructure;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

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
        /**
         * 선택적으로 허용 productId 목록을 설정할 수 있다.
         * 비어있으면 코드 정책(IapProductPolicy)에 위임한다.
         */
        private List<String> allowedProductIds = new ArrayList<>();
    }

    @Getter
    @Setter
    public static class Google {
        private String packageName;
        private String serviceAccountJsonPath;
        private boolean verifyEnabled = false;
    }
}
