package univ.airconnect.ads.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class AdmobVerifierKeyProvider {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String verifierKeysUrl;
    private final Duration cacheTtl;

    private volatile KeyCache cache;

    public AdmobVerifierKeyProvider(
            ObjectMapper objectMapper,
            @Value("${app.ads.admob.verifier-keys-url:https://www.gstatic.com/admob/reward/verifier-keys.json}") String verifierKeysUrl,
            @Value("${app.ads.admob.key-cache-ttl-seconds:3600}") long keyCacheTtlSeconds
    ) {
        this.restClient = RestClient.builder().build();
        this.objectMapper = objectMapper;
        this.verifierKeysUrl = verifierKeysUrl;
        this.cacheTtl = Duration.ofSeconds(Math.max(keyCacheTtlSeconds, 60));
    }

    public PublicKey getPublicKey(String keyId) {
        KeyCache current = cache;
        if (current == null || current.isExpired() || !current.keys.containsKey(keyId)) {
            synchronized (this) {
                current = cache;
                if (current == null || current.isExpired() || !current.keys.containsKey(keyId)) {
                    cache = fetchKeys();
                    current = cache;
                }
            }
        }

        PublicKey publicKey = current.keys.get(keyId);
        if (publicKey == null) {
            throw new IllegalArgumentException("AdMob verifier key not found for key_id=" + keyId);
        }
        return publicKey;
    }

    private KeyCache fetchKeys() {
        try {
            String responseBody = restClient.get()
                    .uri(verifierKeysUrl)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode keysNode = root.path("keys");
            if (!keysNode.isArray()) {
                throw new IllegalStateException("AdMob verifier keys response does not contain keys array");
            }

            Map<String, PublicKey> keys = new HashMap<>();
            for (JsonNode keyNode : keysNode) {
                String keyId = readKeyId(keyNode);
                String pem = keyNode.path("pem").asText("");
                if (keyId.isBlank() || pem.isBlank()) {
                    continue;
                }
                keys.put(keyId, parsePemPublicKey(pem));
            }

            if (keys.isEmpty()) {
                throw new IllegalStateException("AdMob verifier keys are empty");
            }

            log.info("AdMob verifier keys refreshed. keyCount={}", keys.size());
            return new KeyCache(keys, Instant.now().plus(cacheTtl));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to fetch AdMob verifier keys", e);
        }
    }

    private String readKeyId(JsonNode keyNode) {
        String keyId = keyNode.path("keyId").asText("");
        if (keyId.isBlank()) {
            keyId = keyNode.path("key_id").asText("");
        }
        if (keyId.isBlank() && keyNode.has("keyId") && keyNode.get("keyId").isNumber()) {
            keyId = keyNode.get("keyId").asText();
        }
        return keyId;
    }

    private PublicKey parsePemPublicKey(String pem) throws Exception {
        String normalized = pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");

        byte[] keyBytes = Base64.getDecoder().decode(normalized);
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("EC");
        return keyFactory.generatePublic(keySpec);
    }

    private record KeyCache(Map<String, PublicKey> keys, Instant expiresAt) {
        private boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }
}

