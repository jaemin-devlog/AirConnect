package univ.airconnect.department.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import univ.airconnect.department.domain.DepartmentNames;
import univ.airconnect.department.domain.DepartmentStatus;
import univ.airconnect.department.domain.entity.Department;
import univ.airconnect.department.repository.DepartmentRepository;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DepartmentCatalogSeeder implements ApplicationRunner {
    static final String CATALOG_PATH = "departments/hanseo-departments.tsv";

    private final DepartmentRepository departmentRepository;
    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        List<CatalogRow> rows = loadCatalog();
        for (CatalogRow row : rows) {
            Department department = departmentRepository.findByCode(row.code())
                    .orElseGet(() -> Department.create(row.code(), row.name(), row.collegeName(),
                            row.status(), row.displayOrder()));
            department.sync(row.name(), row.collegeName(), row.status(), row.displayOrder());
            departmentRepository.save(department);
        }
        normalizeRenamedDepartments();
        log.info("Department catalog synchronized: departments={}", rows.size());
    }

    List<CatalogRow> loadCatalog() throws Exception {
        ClassPathResource resource = new ClassPathResource(CATALOG_PATH);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                resource.getInputStream(), StandardCharsets.UTF_8))) {
            return reader.lines()
                    .skip(1)
                    .filter(line -> !line.isBlank())
                    .map(this::parse)
                    .toList();
        }
    }

    private CatalogRow parse(String line) {
        String[] columns = line.split("\\t", -1);
        if (columns.length != 5) {
            throw new IllegalStateException("잘못된 학과 카탈로그 행입니다: " + line);
        }
        return new CatalogRow(columns[0], columns[1], columns[2],
                DepartmentStatus.valueOf(columns[3]), Integer.parseInt(columns[4]));
    }

    private void normalizeRenamedDepartments() {
        DepartmentNames.renames().forEach(this::renameUserDepartment);
    }

    private void renameUserDepartment(String previousName, String currentName) {
        jdbcTemplate.update("UPDATE users SET dept_name = ? WHERE dept_name = ?", currentName, previousName);
    }

    record CatalogRow(String code, String collegeName, String name,
                      DepartmentStatus status, int displayOrder) {
    }
}
