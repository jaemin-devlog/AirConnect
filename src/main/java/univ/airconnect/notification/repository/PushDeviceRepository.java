package univ.airconnect.notification.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import univ.airconnect.notification.domain.PushProvider;
import univ.airconnect.notification.domain.entity.PushDevice;

import java.util.List;
import java.util.Optional;

public interface PushDeviceRepository extends JpaRepository<PushDevice, Long> {

    Optional<PushDevice> findByUserIdAndDeviceId(Long userId, String deviceId);

    Optional<PushDevice> findByProviderAndPushToken(PushProvider provider, String pushToken);

    List<PushDevice> findByUserIdAndActiveTrue(Long userId);

    List<PushDevice> findByUserIdAndActiveTrueAndNotificationPermissionGrantedTrue(Long userId);

    Optional<PushDevice> findByIdAndUserId(Long id, Long userId);
}
