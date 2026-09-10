package univ.airconnect.department.dto;

import lombok.Builder;
import lombok.Getter;
import univ.airconnect.department.domain.DepartmentStatus;

@Getter
@Builder
public class DepartmentResponse {
    private Long departmentId;
    private String name;
    private String collegeName;
    private DepartmentStatus status;
}
