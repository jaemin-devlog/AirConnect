package univ.airconnect.iap.apple;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import univ.airconnect.iap.exception.IapErrorCode;
import univ.airconnect.iap.exception.IapException;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.cert.CertPath;
import java.security.cert.CertStore;
import java.security.cert.CertPathValidator;
import java.security.cert.CertificateFactory;
import java.security.cert.CollectionCertStoreParameters;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class AppleSignedTransactionVerifier {

    private static final String EXPECTED_ALGORITHM = "ES256";
    private static final String HEADER_X5C = "x5c";
    private static final String HEADER_ALG = "alg";
    /**
     * Apple Signed Data Certificate 확장 OID.
     */
    private static final String APPLE_SIGNED_DATA_OID = "1.2.840.113635.100.6.11.1";
    /**
     * Apple Worldwide Developer Relations intermediate 인증서 OID.
     */
    private static final String APPLE_WWDR_INTERMEDIATE_OID = "1.2.840.113635.100.6.2.1";
    private static final String APPLE_WWDR_G6_INTERMEDIATE_OID = "1.2.840.113635.100.6.2.14";
    private static final List<String> BUNDLED_APPLE_CA_RESOURCES = List.of(
            "apple-pki/AppleIncRootCertificate.cer",
            "apple-pki/AppleRootCA-G2.cer",
            "apple-pki/AppleRootCA-G3.cer",
            "apple-pki/AppleWWDRCAG2.cer",
            "apple-pki/AppleWWDRCAG3.cer",
            "apple-pki/AppleWWDRCAG4.cer",
            "apple-pki/AppleWWDRCAG5.cer",
            "apple-pki/AppleWWDRCAG6.cer"
    );
    private static final int MAX_CHAIN_LENGTH = 8;

    private final ObjectMapper objectMapper;

    private volatile Set<TrustAnchor> cachedTrustAnchors;
    private volatile List<X509Certificate> cachedBundledCertificates;

    public JsonNode verifyAndExtractPayload(String signedTransactionInfo) {
        try {
            if (signedTransactionInfo == null || signedTransactionInfo.isBlank()) {
                throw new IapException(IapErrorCode.IAP_INVALID_TRANSACTION, "signedTransactionInfo가 비어 있습니다.");
            }

            JsonNode header = decodeHeader(signedTransactionInfo);
            validateHeader(header);

            List<X509Certificate> certificateChain = buildValidationChain(parseCertificateChain(header));
            validateCertificateChain(certificateChain);

            X509Certificate leafCertificate = certificateChain.get(0);
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(leafCertificate.getPublicKey())
                    .build()
                    .parseClaimsJws(signedTransactionInfo)
                    .getBody();

            return objectMapper.valueToTree(claims);
        } catch (IapException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Apple signedTransactionInfo 검증 실패. reason={}", e.getMessage());
            throw new IapException(IapErrorCode.IAP_APPLE_VERIFY_FAILED, "Apple signedTransactionInfo 서명 검증 실패");
        }
    }

    private JsonNode decodeHeader(String jws) throws Exception {
        String[] parts = jws.split("\\.");
        if (parts.length != 3) {
            throw new IapException(IapErrorCode.IAP_INVALID_TRANSACTION, "JWS 형식이 올바르지 않습니다.");
        }
        String decodedHeader = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
        return objectMapper.readTree(decodedHeader);
    }

    private void validateHeader(JsonNode header) {
        String algorithm = header.path(HEADER_ALG).asText(null);
        if (!EXPECTED_ALGORITHM.equals(algorithm)) {
            throw new IapException(IapErrorCode.IAP_INVALID_TRANSACTION, "지원하지 않는 Apple JWS 알고리즘입니다.");
        }

        JsonNode x5cNode = header.get(HEADER_X5C);
        if (x5cNode == null || !x5cNode.isArray() || x5cNode.isEmpty()) {
            throw new IapException(IapErrorCode.IAP_INVALID_TRANSACTION, "Apple JWS 인증서 체인이 누락되었습니다.");
        }
    }

    private List<X509Certificate> parseCertificateChain(JsonNode header) throws Exception {
        JsonNode x5cNode = header.get(HEADER_X5C);
        List<X509Certificate> chain = new ArrayList<>();
        CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");

        Iterator<JsonNode> iterator = x5cNode.elements();
        while (iterator.hasNext()) {
            JsonNode certNode = iterator.next();
            String encodedCertificate = certNode.asText(null);
            if (encodedCertificate == null || encodedCertificate.isBlank()) {
                continue;
            }
            byte[] certificateBytes = Base64.getDecoder().decode(encodedCertificate);
            X509Certificate certificate = (X509Certificate) certificateFactory.generateCertificate(
                    new ByteArrayInputStream(certificateBytes)
            );
            chain.add(certificate);
        }

        if (chain.isEmpty()) {
            throw new IapException(IapErrorCode.IAP_INVALID_TRANSACTION, "Apple JWS 인증서 파싱에 실패했습니다.");
        }

        return chain;
    }

    private void validateCertificateChain(List<X509Certificate> chain) throws Exception {
        Date now = Date.from(Instant.now());
        for (X509Certificate certificate : chain) {
            certificate.checkValidity(now);
        }
        validateChainLinkages(chain);
        validateAppleCertificateMarkers(chain);

        List<X509Certificate> certPathCertificates = new ArrayList<>(chain);
        X509Certificate lastCertificate = certPathCertificates.get(certPathCertificates.size() - 1);
        if (isSelfSigned(lastCertificate)) {
            certPathCertificates.remove(certPathCertificates.size() - 1);
        }

        CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
        CertPath certPath = certificateFactory.generateCertPath(certPathCertificates);

        PKIXParameters parameters = new PKIXParameters(loadTrustAnchors());
        parameters.setRevocationEnabled(false);
        parameters.setDate(now);
        parameters.addCertStore(CertStore.getInstance(
                "Collection",
                new CollectionCertStoreParameters(loadBundledCertificates())
        ));

        CertPathValidator.getInstance("PKIX").validate(certPath, parameters);
    }

    private List<X509Certificate> buildValidationChain(List<X509Certificate> providedChain) {
        List<X509Certificate> chain = new ArrayList<>(providedChain);
        Map<String, X509Certificate> bundledBySubject = new HashMap<>();
        for (X509Certificate certificate : loadBundledCertificates()) {
            bundledBySubject.put(certificate.getSubjectX500Principal().getName(), certificate);
        }

        while (!chain.isEmpty() && chain.size() < MAX_CHAIN_LENGTH) {
            X509Certificate lastCertificate = chain.get(chain.size() - 1);
            if (isSelfSigned(lastCertificate)) {
                break;
            }

            String issuerName = lastCertificate.getIssuerX500Principal().getName();
            X509Certificate issuerCertificate = bundledBySubject.get(issuerName);
            if (issuerCertificate == null || containsCertificate(chain, issuerCertificate)) {
                break;
            }
            chain.add(issuerCertificate);
        }

        return chain;
    }

    private void validateChainLinkages(List<X509Certificate> chain) {
        if (chain.size() < 2) {
            throw new IapException(IapErrorCode.IAP_INVALID_TRANSACTION, "Apple JWS 인증서 체인 길이가 짧습니다.");
        }
        for (int i = 0; i < chain.size() - 1; i++) {
            X509Certificate current = chain.get(i);
            X509Certificate issuer = chain.get(i + 1);
            if (!current.getIssuerX500Principal().equals(issuer.getSubjectX500Principal())) {
                throw new IapException(IapErrorCode.IAP_INVALID_TRANSACTION, "Apple JWS 인증서 체인 연결이 올바르지 않습니다.");
            }
        }
    }

    private void validateAppleCertificateMarkers(List<X509Certificate> chain) {
        X509Certificate leaf = chain.get(0);
        if (leaf.getExtensionValue(APPLE_SIGNED_DATA_OID) == null) {
            throw new IapException(IapErrorCode.IAP_INVALID_TRANSACTION, "Apple Signed Data 인증서가 아닙니다.");
        }

        boolean hasAppleIntermediateMarker = chain.stream()
                .skip(1)
                .anyMatch(cert -> cert.getExtensionValue(APPLE_WWDR_INTERMEDIATE_OID) != null
                        || cert.getExtensionValue(APPLE_WWDR_G6_INTERMEDIATE_OID) != null);
        if (!hasAppleIntermediateMarker) {
            throw new IapException(IapErrorCode.IAP_INVALID_TRANSACTION, "Apple 중간 인증서 마커가 확인되지 않았습니다.");
        }

        X509Certificate rootLikeCert = chain.get(chain.size() - 1);
        String subject = rootLikeCert.getSubjectX500Principal().getName().toLowerCase(Locale.ROOT);
        String issuer = rootLikeCert.getIssuerX500Principal().getName().toLowerCase(Locale.ROOT);
        if (isSelfSigned(rootLikeCert)) {
            if (!subject.contains("apple root ca")) {
                throw new IapException(IapErrorCode.IAP_INVALID_TRANSACTION, "Apple 루트 인증서가 아닙니다.");
            }
            return;
        }
        if (!subject.contains("apple worldwide developer relations")) {
            throw new IapException(IapErrorCode.IAP_INVALID_TRANSACTION, "Apple 중간 인증서 주체가 올바르지 않습니다.");
        }
        if (!issuer.contains("apple root ca")) {
            throw new IapException(IapErrorCode.IAP_INVALID_TRANSACTION, "Apple 중간 인증서 발급자가 올바르지 않습니다.");
        }
    }

    private boolean isSelfSigned(X509Certificate certificate) {
        return certificate.getSubjectX500Principal().equals(certificate.getIssuerX500Principal());
    }

    private boolean containsCertificate(Collection<X509Certificate> certificates, X509Certificate target) {
        return certificates.stream().anyMatch(cert -> cert.equals(target));
    }

    private Set<TrustAnchor> loadTrustAnchors() {
        Set<TrustAnchor> local = cachedTrustAnchors;
        if (local != null) {
            return local;
        }

        synchronized (this) {
            if (cachedTrustAnchors != null) {
                return cachedTrustAnchors;
            }
            try {
                TrustManagerFactory trustManagerFactory =
                        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                trustManagerFactory.init((KeyStore) null);

                Set<TrustAnchor> trustAnchors = new HashSet<>();
                for (TrustManager trustManager : trustManagerFactory.getTrustManagers()) {
                    if (!(trustManager instanceof X509TrustManager x509TrustManager)) {
                        continue;
                    }
                    for (X509Certificate certificate : x509TrustManager.getAcceptedIssuers()) {
                        trustAnchors.add(new TrustAnchor(certificate, null));
                    }
                }

                for (X509Certificate certificate : loadBundledCertificates()) {
                    if (isSelfSigned(certificate)) {
                        trustAnchors.add(new TrustAnchor(certificate, null));
                    }
                }

                if (trustAnchors.isEmpty()) {
                    throw new IllegalStateException("신뢰 가능한 루트 인증서를 찾지 못했습니다.");
                }

                cachedTrustAnchors = trustAnchors;
                return trustAnchors;
            } catch (Exception e) {
                throw new IapException(IapErrorCode.IAP_APPLE_VERIFY_FAILED, "Apple 인증서 신뢰 체인 로딩 실패");
            }
        }
    }

    private List<X509Certificate> loadBundledCertificates() {
        List<X509Certificate> local = cachedBundledCertificates;
        if (local != null) {
            return local;
        }

        synchronized (this) {
            if (cachedBundledCertificates != null) {
                return cachedBundledCertificates;
            }

            try {
                CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
                List<X509Certificate> certificates = new ArrayList<>();

                for (String resourcePath : BUNDLED_APPLE_CA_RESOURCES) {
                    try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
                        if (inputStream == null) {
                            throw new IllegalStateException("Apple 인증서 리소스를 찾을 수 없습니다: " + resourcePath);
                        }
                        certificates.add((X509Certificate) certificateFactory.generateCertificate(inputStream));
                    }
                }

                cachedBundledCertificates = List.copyOf(certificates);
                return cachedBundledCertificates;
            } catch (Exception e) {
                throw new IapException(IapErrorCode.IAP_APPLE_VERIFY_FAILED, "Apple 내장 인증서 로딩 실패");
            }
        }
    }
}
