package univ.airconnect.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import univ.airconnect.groupmatching.domain.GTeamSize;

import java.time.Instant;
import java.util.List;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AdminGroupQueueObserver {
    static final int SAMPLE_LIMIT = 1000;
    private final RedisTemplate<String, Object> redisTemplate;

    public AdminGroupMatchingDtos.Queue observe(GTeamSize size, Long teamId) {
        return observeMany(size, List.of(teamId)).get(teamId);
    }

    public Map<Long, AdminGroupMatchingDtos.Queue> observeMany(GTeamSize size, Collection<Long> teamIds) {
        if (teamIds.isEmpty()) return Map.of();
        // Independent observations, not a DB/Redis atomic snapshot. Never invoke queue recovery.
        try {
            String key = "matching:queue:" + size.name();
            Long length = redisTemplate.opsForList().size(key);
            List<Object> values = redisTemplate.opsForList().range(key, 0, SAMPLE_LIMIT);
            if (length == null || values == null) return unavailable(teamIds);
            boolean truncated = values.size() > SAMPLE_LIMIT || length > SAMPLE_LIMIT;
            int count = Math.min(values.size(), SAMPLE_LIMIT);
            Map<Long, Integer> occurrences = new HashMap<>();
            Map<Long, Integer> positions = new HashMap<>();
            int invalid = 0;
            for (int i = 0; i < count; i++) {
                try {
                    long id = Long.parseLong(String.valueOf(values.get(i)));
                    if (id <= 0) { invalid++; continue; }
                    occurrences.merge(id, 1, Integer::sum);
                    positions.putIfAbsent(id, i + 1);
                } catch (NumberFormatException ignored) { invalid++; }
            }
            Map<Long, AdminGroupMatchingDtos.Queue> result = new HashMap<>();
            Instant observedAt = Instant.now();
            for (Long id : teamIds) {
                int hits = occurrences.getOrDefault(id, 0);
                String presence = hits > 0 ? "FOUND" : truncated ? "OUTSIDE_SAMPLE" : "NOT_OBSERVED";
                result.put(id, new AdminGroupMatchingDtos.Queue(presence, length, count, truncated,
                        positions.get(id), hits, invalid, observedAt));
            }
            return result;
        } catch (RuntimeException ignored) {
            // Do not expose Redis addresses, raw values, or credentials via error text/logging.
            return unavailable(teamIds);
        }
    }

    private Map<Long, AdminGroupMatchingDtos.Queue> unavailable(Collection<Long> ids) {
        Map<Long, AdminGroupMatchingDtos.Queue> result = new HashMap<>();
        var observation = new AdminGroupMatchingDtos.Queue("UNAVAILABLE", null, 0, false, null, 0, 0, Instant.now());
        ids.forEach(id -> result.put(id, observation));
        return result;
    }
}
