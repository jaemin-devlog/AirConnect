package univ.airconnect.iap.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import univ.airconnect.iap.domain.entity.IapEvent;

public interface IapEventRepository extends JpaRepository<IapEvent, Long> {
}

