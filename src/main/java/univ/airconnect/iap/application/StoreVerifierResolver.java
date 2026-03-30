package univ.airconnect.iap.application;

import org.springframework.stereotype.Component;
import univ.airconnect.iap.domain.IapStore;
import univ.airconnect.iap.exception.IapErrorCode;
import univ.airconnect.iap.exception.IapException;

import java.util.List;

@Component
public class StoreVerifierResolver {

    private final List<StorePurchaseVerifier> verifiers;

    public StoreVerifierResolver(List<StorePurchaseVerifier> verifiers) {
        this.verifiers = verifiers;
    }

    public StorePurchaseVerifier resolve(IapStore store) {
        return verifiers.stream()
                .filter(v -> v.supports() == store)
                .findFirst()
                .orElseThrow(() -> new IapException(IapErrorCode.IAP_PROVIDER_NOT_SUPPORTED));
    }
}

