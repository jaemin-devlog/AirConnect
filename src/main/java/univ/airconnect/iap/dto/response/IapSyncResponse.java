package univ.airconnect.iap.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Builder
public class IapSyncResponse {

    private int total;
    private int successCount;
    private int failureCount;
    private List<IapSyncItemResponse> results;
    private OffsetDateTime processedAt;
}
