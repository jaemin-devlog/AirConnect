package univ.airconnect.iap.google;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
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
            String accessToken = accessToken();
            String url = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/"
                    + packageName + "/purchases/products/" + productId + "/tokens/" + purchaseToken;

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
            return objectMapper.readTree(response.getBody());
        } catch (IapException e) {
            throw e;
        } catch (Exception e) {
            throw new IapException(IapErrorCode.IAP_GOOGLE_VERIFY_FAILED, "Google Play 검증 호출 실패");
        }
    }

    private String accessToken() {
        try (FileInputStream fis = new FileInputStream(iapProperties.getGoogle().getServiceAccountJsonPath())) {
            GoogleCredentials credentials = GoogleCredentials.fromStream(fis).createScoped(Collections.singletonList(SCOPE));
            credentials.refreshIfExpired();
            return credentials.getAccessToken().getTokenValue();
        } catch (Exception e) {
            throw new IapException(IapErrorCode.IAP_GOOGLE_VERIFY_FAILED, "Google 서비스 계정 설정 오류");
        }
    }
}

