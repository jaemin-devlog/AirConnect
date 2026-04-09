package univ.airconnect.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import univ.airconnect.user.domain.entity.UserSchoolConsent;

public interface UserSchoolConsentRepository extends JpaRepository<UserSchoolConsent, Long> {
}

