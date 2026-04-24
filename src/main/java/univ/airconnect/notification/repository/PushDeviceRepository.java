package univ.airconnect.notification.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import univ.airconnect.notification.domain.PushProvider;
import univ.airconnect.notification.domain.entity.PushDevice;

import java.util.List;
import java.util.Optional;

public interface PushDeviceRepository extends JpaRepository<PushDevice, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select pushDevice from PushDevice pushDevice where pushDevice.id = :id")
    Optional<PushDevice> findByIdForUpdate(@Param("id") Long id);

    Optional<PushDevice> findByUserIdAndDeviceId(Long userId, String deviceId);

    Optional<PushDevice> findByProviderAndPushToken(PushProvider provider, String pushToken);

    List<PushDevice> findByUserIdAndActiveTrue(Long userId);

    List<PushDevice> findByUserIdAndActiveTrueAndNotificationPermissionGrantedTrue(Long userId);

    Optional<PushDevice> findByIdAndUserId(Long id, Long userId);
}
