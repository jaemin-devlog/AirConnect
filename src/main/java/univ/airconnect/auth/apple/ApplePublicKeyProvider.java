package univ.airconnect.auth.apple;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;

@Component
public class ApplePublicKeyProvider {

    private static final String APPLE_KEYS_URL =
            "https://appleid.apple.com/auth/keys";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PublicKey getPublicKey(String identityToken) {

        try {

            String header = identityToken.split("\\.")[0];
            String decodedHeader =
                    new String(Base64.getUrlDecoder().decode(header));

            JsonNode headerNode =
                    objectMapper.readTree(decodedHeader);

            String kid = headerNode.get("kid").asText();
            String alg = headerNode.get("alg").asText();

            String keysJson =
                    restTemplate.getForObject(APPLE_KEYS_URL, String.class);

            JsonNode keys =
                    objectMapper.readTree(keysJson).get("keys");

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