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
    @Query("SELECT d FROM PushDevice d WHERE d.id = :id")
    Optional<PushDevice> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM PushDevice d WHERE d.userId = :userId AND d.deviceId = :deviceId")
    Optional<PushDevice> findByUserIdAndDeviceIdForUpdate(@Param("userId") Long userId,
                                                          @Param("deviceId") String deviceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM PushDevice d WHERE d.provider = :provider AND d.pushToken = :pushToken")
    Optional<PushDevice> findByProviderAndPushTokenForUpdate(@Param("provider") PushProvider provider,
                                                             @Param("pushToken") String pushToken);

    List<PushDevice> findByUserIdAndActiveTrue(Long userId);

    List<PushDevice> findByUserIdAndActiveTrueAndNotificationPermissionGrantedTrue(Long userId);

    Optional<PushDevice> findByIdAndUserId(Long id, Long userId);

    long deleteByUserId(Long userId);
}
