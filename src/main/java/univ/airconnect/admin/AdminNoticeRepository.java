package univ.airconnect.admin;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface AdminNoticeRepository extends JpaRepository<AdminNotice, Long> {

    Page<AdminNotice> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);

    List<AdminNotice> findAllByOrderByCreatedAtDescIdDesc();

    @Query("SELECT COALESCE(SUM(n.recipientCount), 0) FROM AdminNotice n")
    long sumRecipientCount();
}
