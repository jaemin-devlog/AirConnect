package univ.airconnect.auth.apple;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import univ.airconnect.auth.service.oauth.apple.AppleAuthProperties;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;

@Component
@RequiredArgsConstructor
public class ApplePublicKeyProvider {

    private final AppleAuthProperties appleAuthProperties;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PublicKey getPublicKey(String identityToken) {

        try {

            String header = identityToken.split("\\.")[0];
            String decodedHeader =
                    new String(Base64.getUrlDecoder().decode(header), StandardCharsets.UTF_8);

            JsonNode headerNode =
                    objectMapper.readTree(decodedHeader);

            JsonNode kidNode = headerNode.get("kid");
            JsonNode algNode = headerNode.get("alg");
            if (kidNode == null || algNode == null) {
                throw new IllegalStateException("Apple identity token header is invalid");
            }
            String kid = kidNode.asText();
            String alg = algNode.asText();

            String keysUrl = appleAuthProperties.resolveLoginJwksUrl();
            if (keysUrl == null || keysUrl.isBlank()) {
                throw new IllegalStateException("Missing required property: apple.login.jwks-url");
            }
            String keysJson = restTemplate.getForObject(keysUrl, String.class);
            if (keysJson == null || keysJson.isBlank()) {
                throw new IllegalStateException("Apple jwks response is empty");
            }

            JsonNode keys =
                    objectMapper.readTree(keysJson).get("keys");
            if (keys == null || !keys.isArray()) {
                throw new IllegalStateException("Apple jwks payload is invalid");
            }

            for (JsonNode key : keys) {

                if (kid.equals(key.get("kid").asText())
                        && alg.equals(key.get("alg").asText())) {

                    String n = key.get("n").asText();
                    String e = key.get("e").asText();

                    return generatePublicKey(n, e);
                }
            }

            throw new RuntimeException("Apple public key not found");

        } catch (Exception e) {
            throw new RuntimeException("Apple public key error", e);
        }
    }

    private PublicKey generatePublicKey(String n, String e)
            throws Exception {

        byte[] modulusBytes =
                Base64.getUrlDecoder().decode(n);

        byte[] exponentBytes =
                Base64.getUrlDecoder().decode(e);

        BigInteger modulus =
                new BigInteger(1, modulusBytes);

        BigInteger exponent =
                new BigInteger(1, exponentBytes);

        RSAPublicKeySpec spec =
                new RSAPublicKeySpec(modulus, exponent);

        KeyFactory keyFactory =
                KeyFactory.getInstance("RSA");

        return keyFactory.generatePublic(spec);
    }
}
