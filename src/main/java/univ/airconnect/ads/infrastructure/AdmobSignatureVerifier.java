package univ.airconnect.ads.infrastructure;

import com.google.crypto.tink.apps.rewardedads.RewardedAdsVerifier;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AdmobSignatureVerifier {

    private final RewardedAdsVerifier verifier;

    public AdmobSignatureVerifier() throws Exception {
        this.verifier = new RewardedAdsVerifier.Builder()
                .fetchVerifyingPublicKeysWith(RewardedAdsVerifier.KEYS_DOWNLOADER_INSTANCE_PROD)
                .build();
    }

    public boolean verify(HttpServletRequest request) {
        try {
            String rewardUrl = request.getRequestURL().toString()
                    + (request.getQueryString() == null ? "" : "?" + request.getQueryString());

            verifier.verify(rewardUrl);
            return true;
        } catch (Exception e) {
            log.warn("AdMob signature verification failed: {}", e.getMessage(), e);
            return false;
        }
    }
}