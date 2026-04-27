package univ.airconnect.verification.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import univ.airconnect.verification.domain.entity.VerifiedSchoolEmail;

import java.util.Optional;

public interface VerifiedSchoolEmailRepository extends JpaRepository<VerifiedSchoolEmail, Long> {

    boolean existsByEmailIgnoreCase(String email);

    Optional<VerifiedSchoolEmail> findByEmailIgnoreCase(String email);
}
