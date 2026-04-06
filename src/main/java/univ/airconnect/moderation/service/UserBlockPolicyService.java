package univ.airconnect.moderation.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import univ.airconnect.moderation.repository.UserBlockRepository;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserBlockPolicyService {

    private final UserBlockRepository userBlockRepository;

    public boolean hasBlockRelation(Long userAId, Long userBId) {
        if (userAId == null || userBId == null) {
            return false;
        }
        if (userAId.equals(userBId)) {
            return false;
        }
        return userBlockRepository.existsRelation(userAId, userBId);
    }

    public Set<Long> resolveBlockedCounterpartIds(Long userId, Collection<Long> targetUserIds) {
        if (userId == null || targetUserIds == null || targetUserIds.isEmpty()) {
            return Collections.emptySet();
        }

        Set<Long> targets = new HashSet<>(targetUserIds);
        targets.remove(userId);
        if (targets.isEmpty()) {
            return Collections.emptySet();
        }

        Set<Long> blocked = new HashSet<>(userBlockRepository.findBlockedUserIds(userId, targets));
        blocked.addAll(userBlockRepository.findBlockerUserIds(userId, targets));
        return blocked;
    }

    public Optional<Long> findAnyBlockedCounterpart(Long userId, Collection<Long> targetUserIds) {
        Set<Long> blocked = resolveBlockedCounterpartIds(userId, targetUserIds);
        return blocked.stream().findFirst();
    }
}
