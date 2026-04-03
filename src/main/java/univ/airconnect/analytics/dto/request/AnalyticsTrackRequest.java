package univ.airconnect.analytics.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsTrackRequest {

    @Valid
    @NotEmpty
    private List<AnalyticsTrackItemRequest> events;
}
