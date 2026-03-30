package univ.airconnect.iap.application;

import univ.airconnect.iap.domain.IapStore;

public interface StorePurchaseVerifier {

    IapStore supports();

    StoreVerificationResult verify(Long userId, Object request);
}

