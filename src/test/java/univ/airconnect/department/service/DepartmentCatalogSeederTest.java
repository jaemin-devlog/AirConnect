package univ.airconnect.department.service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import univ.airconnect.department.domain.DepartmentNames;
import univ.airconnect.department.domain.DepartmentStatus;
import univ.airconnect.department.repository.DepartmentRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DepartmentCatalogSeederTest {
    @Test
    void catalogContainsApprovedDepartmentsWithCurrentNamesOnly() throws Exception {
        DepartmentCatalogSeeder seeder = new DepartmentCatalogSeeder(mock(DepartmentRepository.class),
                mock(JdbcTemplate.class));

        var rows = seeder.loadCatalog();

        assertThat(rows).hasSize(63);
        assertThat(rows).filteredOn(row -> row.status() == DepartmentStatus.ACTIVE).hasSize(46);
        assertThat(rows).filteredOn(row -> row.status() == DepartmentStatus.LEGACY).hasSize(17);
        assertThat(rows).extracting(DepartmentCatalogSeeder.CatalogRow::name)
                .contains("문화유산보존학과", "뮤직프로덕션학과", "디지털산업디자인학과")
                .doesNotContain("문화재보존학과", "실용음악과", "산업디자인학과");
    }

    @Test
    void synchronizingCatalogRewritesPreviousUserDepartmentNames() throws Exception {
        DepartmentRepository repository = mock(DepartmentRepository.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(repository.findByCode(any())).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        new DepartmentCatalogSeeder(repository, jdbcTemplate)
                .run(new DefaultApplicationArguments(new String[0]));

        verify(repository, times(63)).save(any());
        verify(jdbcTemplate).update("UPDATE users SET dept_name = ? WHERE dept_name = ?",
                "문화유산보존학과", "문화재보존학과");
        verify(jdbcTemplate).update("UPDATE users SET dept_name = ? WHERE dept_name = ?",
                "뮤직프로덕션학과", "실용음악과");
        verify(jdbcTemplate).update("UPDATE users SET dept_name = ? WHERE dept_name = ?",
                "디지털산업디자인학과", "산업디자인학과");
    }

    @Test
    void previousNamesAreCanonicalizedForNewSignups() {
        assertThat(DepartmentNames.canonicalize("문화재보존학과")).isEqualTo("문화유산보존학과");
        assertThat(DepartmentNames.canonicalize("실용음악과")).isEqualTo("뮤직프로덕션학과");
        assertThat(DepartmentNames.canonicalize("산업디자인학과")).isEqualTo("디지털산업디자인학과");
    }
}
