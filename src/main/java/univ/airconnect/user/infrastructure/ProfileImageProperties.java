package univ.airconnect.user.infrastructure;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import univ.airconnect.user.domain.UserStatus;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.upload")
public class ProfileImageProperties {

    private String profileImageDir = "/tmp/airconnect/profile-images";
    private String profileImageUrlBase = "http://localhost:8080/api/v1/users/profile-images";
    private long profileImageMaxBytes = 5L * 1024L * 1024L;
    private long profileImageMaxPixels = 25_000_000L;
    private List<String> profileImageAllowedFormats = new ArrayList<>(List.of("jpeg", "jpg", "png"));
    private List<UserStatus> profileImageHiddenUserStatuses = new ArrayList<>(List.of(UserStatus.DELETED, UserStatus.SUSPENDED));
}
