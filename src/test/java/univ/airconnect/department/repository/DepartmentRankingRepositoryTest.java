package univ.airconnect.department.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import univ.airconnect.auth.domain.entity.SocialProvider;
import univ.airconnect.department.domain.DepartmentStatus;
import univ.airconnect.department.domain.entity.Department;
import univ.airconnect.matching.domain.entity.MatchingConnection;
import univ.airconnect.matching.repository.MatchingConnectionRepository;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.repository.UserRepository;

import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class DepartmentRankingRepositoryTest {
    @Autowired DepartmentRepository departmentRepository;
    @Autowired UserRepository userRepository;
    @Autowired MatchingConnectionRepository matchingConnectionRepository;

    @Test
    void ranksAllDepartmentsBySentAndReceivedRequests() {
        saveDepartment("D1", "디지털산업디자인학과", "디자인융합학부", 1);
        saveDepartment("D2", "항공운항학과", "항공학부", 2);
        saveDepartment("D3", "항공컴퓨터학과", "항공융합학부(이전)", 3);

        User designUser = saveUser("u1", "디지털산업디자인학과");
        User currentDesignUser = saveUser("u2", "디지털산업디자인학과");
        User aviationUser = saveUser("u3", "항공운항학과");
        matchingConnectionRepository.save(MatchingConnection.createPending(designUser.getId(), aviationUser.getId()));
        matchingConnectionRepository.save(MatchingConnection.createPending(currentDesignUser.getId(), designUser.getId()));

        Map<String, Long> counts = departmentRepository.findAllRankedByMatchingRequests().stream()
                .collect(Collectors.toMap(DepartmentRankingProjection::getDeptName,
                        DepartmentRankingProjection::getRequestCount));

        assertThat(counts).containsEntry("디지털산업디자인학과", 3L)
                .containsEntry("항공운항학과", 1L)
                .containsEntry("항공컴퓨터학과", 0L);
    }

    private Department saveDepartment(String code, String name, String college, int order) {
        Department department = departmentRepository.save(
                Department.create(code, name, college,
                        "D3".equals(code) ? DepartmentStatus.LEGACY : DepartmentStatus.ACTIVE, order));
        return department;
    }

    private User saveUser(String socialId, String department) {
        User user = User.create(SocialProvider.KAKAO, socialId);
        user.completeSignUp(socialId, socialId, 20260001, department);
        return userRepository.save(user);
    }
}
