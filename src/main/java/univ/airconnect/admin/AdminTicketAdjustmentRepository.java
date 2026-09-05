package univ.airconnect.admin;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AdminTicketAdjustmentRepository extends JpaRepository<AdminTicketAdjustment, Long> {
    Optional<AdminTicketAdjustment> findByOperationId(String operationId);
}
