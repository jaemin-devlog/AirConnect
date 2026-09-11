package univ.airconnect.groupmatching.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import univ.airconnect.groupmatching.domain.entity.GTeamReadyState;

import java.util.List;

@Repository
public interface GTeamReadyStateRepository extends JpaRepository<GTeamReadyState, Long> {

    List<GTeamReadyState> findByTeamRoomIdOrderByIdAsc(Long teamRoomId);
}
