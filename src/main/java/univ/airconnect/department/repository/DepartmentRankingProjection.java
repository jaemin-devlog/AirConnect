package univ.airconnect.department.repository;

public interface DepartmentRankingProjection {
    Long getDepartmentId();
    String getDeptName();
    String getCollegeName();
    String getStatus();
    long getRequestCount();
}
