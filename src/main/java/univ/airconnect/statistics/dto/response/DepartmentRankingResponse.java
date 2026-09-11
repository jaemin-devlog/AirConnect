package univ.airconnect.statistics.dto.response;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@Builder
@EqualsAndHashCode
public class DepartmentRankingResponse {

    private final int rank;
    private final Long departmentId;
    private final String deptName;
    private final String collegeName;
    private final String status;
    private final long requestCount;
}
