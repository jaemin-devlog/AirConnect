package univ.airconnect.ticket.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import univ.airconnect.ticket.domain.entity.FestivalCoupon;

import java.util.List;
import java.util.Optional;

public interface FestivalCouponRepository extends JpaRepository<FestivalCoupon, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM FestivalCoupon c WHERE c.code = :code")
    Optional<FestivalCoupon> findByCodeForUpdate(@Param("code") String code);

    @Query("SELECT c.code FROM FestivalCoupon c")
    List<String> findAllCodes();
}
