package univ.airconnect.iap.google;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import univ.airconnect.iap.exception.IapErrorCode;
import univ.airconnect.iap.exception.IapException;
import univ.airconnect.iap.infrastructure.IapProperties;

import java.io.FileInputStream;
import java.util.Collections;

@Component
@Slf4j
public class GooglePlayApiClient {

    private static final String SCOPE = "https://www.googleapis.com/auth/androidpublisher";

    private final IapProperties iapProperties;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    public GooglePlayApiClient(IapProperties iapProperties, ObjectMapper objectMapper) {
        this.iapProperties = iapProperties;
        this.objectMapper = objectMapper;
    }

    public JsonNode verifyProductPurchase(String packageName, String productId, String purchaseToken) {
        try {
            log.info("GooglePlay API verify call started. packageName={}, productId={}, purchaseTokenExists={}",
                    packageName, productId, purchaseToken != null);
            String accessToken = accessToken();
            String url = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/"
                    + packageName + "/purchases/products/" + productId + "/tokens/" + purchaseToken;

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
            log.info("GooglePlay API verify call completed. packageName={}, productId={}, httpStatus={}",
                    packageName, productId, response.getStatusCode().value());
            return objectMapper.readTree(response.getBody());
        } catch (IapException e) {
            log.warn("GooglePlay API verify business failure. packageName={}, productId={}, code={}, message={}",
                    packageName, productId, e.getErrorCode().getCode(), e.getMessage());
            throw e;
        } catch (Exception e) {
            log.warn("GooglePlay API verify unexpected failure. packageName={}, productId={}, reason={}",
                    packageName, productId, e.getMessage());
            throw new IapException(IapErrorCode.IAP_GOOGLE_VERIFY_FAILED, "Google Play 검증 호출 실패");
        }
    }

    private String accessToken() {
        try (FileInputStream fis = new FileInputStream(iapProperties.getGoogle().getServiceAccountJsonPath())) {
            log.info("GooglePlay API access token issuance started. serviceAccountPathExists={}",
                    iapProperties.getGoogle().getServiceAccountJsonPath() != null);
            GoogleCredentials credentials = GoogleCredentials.fromStream(fis).createScoped(Collections.singletonList(SCOPE));
            credentials.refreshIfExpired();
            log.info("GooglePlay API access token issuance completed.");
            return credentials.getAccessToken().getTokenValue();
        } catch (Exception e) {
            log.warn("GooglePlay API access token issuance failed. reason={}", e.getMessage());
            throw new IapException(IapErrorCode.IAP_GOOGLE_VERIFY_FAILED, "Google 서비스 계정 설정 오류");
        }
    }
}

