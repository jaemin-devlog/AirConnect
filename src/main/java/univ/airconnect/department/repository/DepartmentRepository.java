package univ.airconnect.department.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import univ.airconnect.department.domain.entity.Department;

import java.util.List;
import java.util.Optional;

public interface DepartmentRepository extends JpaRepository<Department, Long> {
    Optional<Department> findByCode(String code);

    boolean existsByName(String name);

    List<Department> findAllByOrderByDisplayOrderAsc();

    @Query(value = """
            SELECT d.id AS departmentId,
                   d.name AS deptName,
                   d.college_name AS collegeName,
                   d.status AS status,
                   COUNT(requests.department_id) AS requestCount
            FROM departments d
            LEFT JOIN (
                SELECT d_requester.id AS department_id
                FROM matching_connections mc
                JOIN users requester ON requester.id = mc.requester_id
                JOIN departments d_requester ON d_requester.name = requester.dept_name
                UNION ALL
                SELECT d_receiver.id AS department_id
                FROM matching_connections mc
                JOIN users receiver
                  ON receiver.id = CASE
                        WHEN mc.requester_id = mc.user1_id THEN mc.user2_id
                        ELSE mc.user1_id
                     END
                JOIN departments d_receiver ON d_receiver.name = receiver.dept_name
            ) requests ON requests.department_id = d.id
            GROUP BY d.id, d.name, d.college_name, d.status
            ORDER BY COUNT(requests.department_id) DESC, d.name ASC
            """, nativeQuery = true)
    List<DepartmentRankingProjection> findAllRankedByMatchingRequests();

    @Query(value = """
            SELECT d.id AS departmentId,
                   d.name AS deptName,
                   d.college_name AS collegeName,
                   d.status AS status,
                   COUNT(requests.department_id) AS requestCount
            FROM departments d
            LEFT JOIN (
                SELECT d_requester.id AS department_id
                FROM matching_connections mc
                JOIN users requester ON requester.id = mc.requester_id
                JOIN departments d_requester ON d_requester.name = requester.dept_name
                UNION ALL
                SELECT d_receiver.id AS department_id
                FROM matching_connections mc
                JOIN users receiver
                  ON receiver.id = CASE
                        WHEN mc.requester_id = mc.user1_id THEN mc.user2_id
                        ELSE mc.user1_id
                     END
                JOIN departments d_receiver ON d_receiver.name = receiver.dept_name
            ) requests ON requests.department_id = d.id
            GROUP BY d.id, d.name, d.college_name, d.status
            ORDER BY COUNT(requests.department_id) DESC, d.name ASC
            LIMIT 1
            """, nativeQuery = true)
    Optional<DepartmentRankingProjection> findTopRankedByMatchingRequests();
}
