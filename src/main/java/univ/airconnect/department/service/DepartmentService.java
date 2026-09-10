package univ.airconnect.department.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import univ.airconnect.department.dto.DepartmentResponse;
import univ.airconnect.department.repository.DepartmentRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DepartmentService {
    private final DepartmentRepository departmentRepository;

    public List<DepartmentResponse> findAll() {
        return departmentRepository.findAllByOrderByDisplayOrderAsc().stream()
                .map(department -> DepartmentResponse.builder()
                        .departmentId(department.getId())
                        .name(department.getName())
                        .collegeName(department.getCollegeName())
                        .status(department.getStatus())
                        .build())
                .toList();
    }
}
