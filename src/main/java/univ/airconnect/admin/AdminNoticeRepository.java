package univ.airconnect.admin;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AdminNoticeRepository extends JpaRepository<AdminNotice, Long> {

    Page<AdminNotice> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);

    List<AdminNotice> findAllByOrderByCreatedAtDescIdDesc();
}
