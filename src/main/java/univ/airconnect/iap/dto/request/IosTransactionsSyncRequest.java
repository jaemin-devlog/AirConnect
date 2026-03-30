package univ.airconnect.iap.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class IosTransactionsSyncRequest {

    @NotEmpty
    @Valid
    private List<IosSyncItem> transactions;

    @Getter
    @NoArgsConstructor
    public static class IosSyncItem {
        private String signedTransactionInfo;
        private String transactionId;
        private String appAccountToken;
    }
}

