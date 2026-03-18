package univ.airconnect.matching.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import univ.airconnect.matching.domain.entity.MatchingQueueEntry;
import univ.airconnect.user.domain.Gender;

import java.util.List;
import java.util.Optional;

public interface MatchingQueueEntryRepository extends JpaRepository<MatchingQueueEntry, Long> {

    Optional<MatchingQueueEntry> findByUserId(Long userId);

    Optional<MatchingQueueEntry> findByUserIdAndActiveTrue(Long userId);

    @Query("""
            select q
            from MatchingQueueEntry q
            where q.active = true
              and q.userId <> :userId
              and exists (
                  select p.userId
                  from UserProfile p
                  where p.userId = q.userId
                    and p.gender is not null
                    and p.gender <> :requesterGender
              )
              and exists (
                  select u.id
                  from User u
                  where u.id = q.userId and u.status = 'ACTIVE'
              )
              and not exists (
                  select e.id
                  from MatchingExposure e
                  where e.userId = :userId and e.candidateUserId = q.userId
              )
              and not exists (
                  select c.id
                  from MatchingConnection c
                  where (c.user1Id = :userId and c.user2Id = q.userId)
                     or (c.user1Id = q.userId and c.user2Id = :userId)
              )
            order by q.enteredAt asc
            """)
    List<MatchingQueueEntry> findAvailableCandidates(
            @Param("userId") Long userId,
            @Param("requesterGender") Gender requesterGender,
            Pageable pageable
    );
}


