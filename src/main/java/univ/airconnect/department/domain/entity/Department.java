package univ.airconnect.department.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import univ.airconnect.department.domain.DepartmentStatus;

@Entity
@Getter
@Table(name = "departments", uniqueConstraints = {
        @UniqueConstraint(name = "uk_departments_code", columnNames = "code"),
        @UniqueConstraint(name = "uk_departments_name", columnNames = "name")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Department {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "college_name", nullable = false, length = 100)
    private String collegeName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DepartmentStatus status;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    private Department(String code, String name, String collegeName,
                       DepartmentStatus status, int displayOrder) {
        this.code = code;
        sync(name, collegeName, status, displayOrder);
    }

    public static Department create(String code, String name, String collegeName,
                                    DepartmentStatus status, int displayOrder) {
        return new Department(code, name, collegeName, status, displayOrder);
    }

    public void sync(String name, String collegeName, DepartmentStatus status, int displayOrder) {
        this.name = name;
        this.collegeName = collegeName;
        this.status = status;
        this.displayOrder = displayOrder;
    }
}
