package univ.airconnect.iap.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;
import univ.airconnect.iap.dto.request.AndroidPurchasesSyncRequest;
import univ.airconnect.iap.dto.request.IosTransactionVerifyRequest;
import univ.airconnect.iap.dto.request.IosTransactionsSyncRequest;

import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class IapRequestValidationTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void rejectsOversizedRestoreBatchAndNullOrUnvalidatedItems() throws Exception {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var ios = mapper.convertValue(Map.of("transactions", Collections.nCopies(101,
                    Map.of("signedTransactionInfo", "signed-fixture"))), IosTransactionsSyncRequest.class);
            assertThat(validator.validate(ios)).isNotEmpty();

            var android = mapper.readValue("{\"purchases\":[null,{}]}", AndroidPurchasesSyncRequest.class);
            assertThat(validator.validate(android)).hasSize(3);

            var malformedIos = mapper.readValue("{\"transactions\":[{}]}", IosTransactionsSyncRequest.class);
            assertThat(validator.validate(malformedIos)).isNotEmpty();
        }
    }

    @Test
    void acceptsExistingPayloadShapeAndRejectsOversizedSignedPayload() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var existing = new IosTransactionVerifyRequest("signed-fixture", "123456789", null);
            assertThat(validator.validate(existing)).isEmpty();
            var oversized = new IosTransactionVerifyRequest("x".repeat(65_537), "123456789", null);
            assertThat(validator.validate(oversized)).isNotEmpty();
        }
    }
}
