package univ.airconnect.auth.service.oauth.apple;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

@Component
public class AppleTokenRevocationClient {

    private final AppleAuthProperties appleAuthProperties;
    private final RestTemplate restTemplate = new RestTemplate();

    public AppleTokenRevocationClient(AppleAuthProperties appleAuthProperties) {
        this.appleAuthProperties = appleAuthProperties;
    }

    public void revoke(String token, String tokenTypeHint, String clientSecret) {
        String endpoint = appleAuthProperties.getRevoke().getEndpoint();
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalStateException("Missing required property: apple.revoke.endpoint");
        }

        String clientId = appleAuthProperties.resolveRevokeClientId();
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalStateException("Missing required property: apple.revoke.client-id");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("token", token);
        if (tokenTypeHint != null && !tokenTypeHint.isBlank()) {
            form.add("token_type_hint", tokenTypeHint);
        }

        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(form, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(endpoint, entity, String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new IllegalStateException("Apple revoke failed with status: " + response.getStatusCode().value());
            }
        } catch (HttpStatusCodeException e) {
            throw new IllegalStateException("Apple revoke failed with status: " + e.getStatusCode().value(), e);
        } catch (Exception e) {
            throw new IllegalStateException("Apple revoke call failed", e);
        }
    }
}
