package univ.airconnect.notification.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * FCM 발송이 활성화된 경우에만 Firebase Admin SDK 빈을 생성한다.
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(FirebasePushProperties.class)
@ConditionalOnProperty(prefix = "notification.push.fcm", name = "enabled", havingValue = "true")
public class FirebasePushConfig {

    private static final String DEFAULT_APP_NAME = "airconnect-notification";

    /**
     * 알림 모듈이 독립된 Firebase 클라이언트 인스턴스를 사용하도록 이름 있는 FirebaseApp을 생성한다.
     */
    @Bean
    public FirebaseApp notificationFirebaseApp(FirebasePushProperties properties) throws IOException {
        String appName = hasText(properties.appName()) ? properties.appName() : DEFAULT_APP_NAME;

        for (FirebaseApp firebaseApp : FirebaseApp.getApps()) {
            if (firebaseApp.getName().equals(appName)) {
                return firebaseApp;
            }
        }

        FirebaseOptions.Builder optionsBuilder = FirebaseOptions.builder()
                .setCredentials(loadCredentials(properties));
        if (hasText(properties.projectId())) {
            optionsBuilder.setProjectId(properties.projectId());
        }

        FirebaseApp firebaseApp = FirebaseApp.initializeApp(optionsBuilder.build(), appName);
        log.info("FirebaseApp initialized for push delivery: appName={}, projectId={}",
                appName, properties.projectId());
        return firebaseApp;
    }

    /**
     * 알림 전용 FirebaseApp을 사용하는 FirebaseMessaging 빈을 노출한다.
     */
    @Bean
    public FirebaseMessaging firebaseMessaging(FirebaseApp notificationFirebaseApp) {
        return FirebaseMessaging.getInstance(notificationFirebaseApp);
    }

    /**
     * 설정된 JSON 파일에서 인증 정보를 읽거나, 경로가 없으면 ADC에서 인증 정보를 읽는다.
     */
    private GoogleCredentials loadCredentials(FirebasePushProperties properties) throws IOException {
        if (hasText(properties.credentialsPath())) {
            Path credentialsPath = Path.of(properties.credentialsPath());
            try (InputStream inputStream = Files.newInputStream(credentialsPath)) {
                return GoogleCredentials.fromStream(inputStream);
            }
        }
        return GoogleCredentials.getApplicationDefault();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
