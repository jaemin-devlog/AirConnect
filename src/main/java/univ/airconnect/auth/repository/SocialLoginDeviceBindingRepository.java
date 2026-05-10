package univ.airconnect.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import univ.airconnect.auth.domain.entity.SocialLoginDeviceBinding;

import java.util.Optional;

public interface SocialLoginDeviceBindingRepository extends JpaRepository<SocialLoginDeviceBinding, Long> {

    Optional<SocialLoginDeviceBinding> findByDeviceId(String deviceId);
}
