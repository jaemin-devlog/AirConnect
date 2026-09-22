package univ.airconnect.iap.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class IosTransactionsSyncRequest {

    @NotEmpty
    @Size(max = 100)
    @Valid
    private List<@NotNull IosSyncItem> transactions;

    @Getter
    @NoArgsConstructor
    public static class IosSyncItem {
        @NotBlank
        @Size(max = 65_536)
        private String signedTransactionInfo;
        @Size(max = 80)
        private String transactionId;
        @Size(max = 120)
        private String appAccountToken;
    }
}

