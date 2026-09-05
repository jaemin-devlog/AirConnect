package univ.airconnect.admin;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import univ.airconnect.groupmatching.domain.GTeamSize;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** No Redis connection: verifies observational parsing and an LLEN/LRANGE-only command contract. */
class AdminGroupQueueObserverTest {
    private static final Long TEAM_ID = 42L;
    private RedisTemplate<String, Object> redis;
    private ListOperations<String, Object> lists;
    private AdminGroupQueueObserver observer;
    private boolean emptyBatch;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        emptyBatch = false;
        redis = mock(RedisTemplate.class);
        lists = mock(ListOperations.class);
        when(redis.opsForList()).thenReturn(lists);
        observer = new AdminGroupQueueObserver(redis);
    }

    @AfterEach
    void onlyListSizeAndRangeWereUsed() {
        if (emptyBatch) {
            verifyNoInteractions(redis, lists);
            return;
        }
        // Explicitly mark the permitted accessor/commands as verified. Any SET, delete,
        // Lua execution, list removal/push, or any other Redis operation fails this check.
        verify(redis, atLeastOnce()).opsForList();
        verify(lists, atMostOnce()).size(anyString());
        verify(lists, atMostOnce()).range(anyString(), anyLong(), anyLong());
        verifyNoMoreInteractions(redis, lists);
    }

    @ParameterizedTest
    @EnumSource(GTeamSize.class)
    void observesTheRequestedSizeOnly_andFindsOneBasedRawPosition(GTeamSize size) {
        String key = key(size);
        when(lists.size(key)).thenReturn(3L);
        when(lists.range(key, 0, AdminGroupQueueObserver.SAMPLE_LIMIT))
                .thenReturn(List.of("700", TEAM_ID.toString(), "701"));
        Instant before = Instant.now();

        var result = observer.observe(size, TEAM_ID);

        assertThat(result.presence()).isEqualTo("FOUND");
        assertThat(result.length()).isEqualTo(3);
        assertThat(result.sampledCount()).isEqualTo(3);
        assertThat(result.firstPosition()).isEqualTo(2);
        assertThat(result.occurrences()).isEqualTo(1);
        assertThat(result.invalidCount()).isZero();
        assertThat(result.truncated()).isFalse();
        assertThat(result.observedAt()).isBetween(before, Instant.now());
        verify(lists).size(key);
        verify(lists).range(key, 0, AdminGroupQueueObserver.SAMPLE_LIMIT);
    }

    @Test
    void foreignTeamIds_areNotMistakenForTheSelectedTeamOrInvalidEntries() {
        sample(3L, List.of("700", "700", "701"));

        var result = observer.observe(GTeamSize.TWO, TEAM_ID);

        assertThat(result.presence()).isEqualTo("NOT_OBSERVED");
        assertThat(result.occurrences()).isZero();
        assertThat(result.invalidCount()).isZero();
        assertThat(result.firstPosition()).isNull();
        assertThat(result.sampledCount()).isEqualTo(3);
    }

    @Test
    void selectedTeamDuplicates_areCountedWithoutDeduplication() {
        sample(4L, List.of("42", "800", "42", "42"));

        var result = observer.observe(GTeamSize.TWO, TEAM_ID);

        assertThat(result.presence()).isEqualTo("FOUND");
        assertThat(result.firstPosition()).isEqualTo(1);
        assertThat(result.occurrences()).isEqualTo(3);
        assertThat(result.sampledCount()).isEqualTo(4);
        assertThat(result.invalidCount()).isZero();
    }

    @Test
    void malformedNullNonPositiveAndOverflowValues_areCountedWithoutReturningRawValues() {
        sample(7L, Arrays.asList("not-a-team", null, "0", "-1", "9223372036854775808", "1.5", "42"));

        var result = observer.observe(GTeamSize.TWO, TEAM_ID);

        assertThat(result.presence()).isEqualTo("FOUND");
        assertThat(result.invalidCount()).isEqualTo(6);
        assertThat(result.occurrences()).isEqualTo(1);
        assertThat(result.firstPosition()).isEqualTo(7);
        assertThat(result.toString()).doesNotContain("not-a-team", "9223372036854775808");
    }

    @Test
    void emptyQueue_isObservedEmptyRatherThanUnavailable() {
        sample(0L, List.of());

        var result = observer.observe(GTeamSize.TWO, TEAM_ID);

        assertThat(result.presence()).isEqualTo("NOT_OBSERVED");
        assertThat(result.length()).isZero();
        assertThat(result.sampledCount()).isZero();
        assertThat(result.truncated()).isFalse();
    }

    @Test
    void exactlyOneThousandEntries_isCompleteAndIncludesTheLastPosition() {
        List<Object> values = new ArrayList<>(Collections.nCopies(AdminGroupQueueObserver.SAMPLE_LIMIT, "700"));
        values.set(AdminGroupQueueObserver.SAMPLE_LIMIT - 1, TEAM_ID.toString());
        sample((long) AdminGroupQueueObserver.SAMPLE_LIMIT, values);

        var result = observer.observe(GTeamSize.TWO, TEAM_ID);

        assertThat(result.presence()).isEqualTo("FOUND");
        assertThat(result.firstPosition()).isEqualTo(AdminGroupQueueObserver.SAMPLE_LIMIT);
        assertThat(result.sampledCount()).isEqualTo(AdminGroupQueueObserver.SAMPLE_LIMIT);
        assertThat(result.truncated()).isFalse();
    }

    @Test
    void sentinelBeyondSample_isNotUsedToClaimPresenceOrAbsence() {
        List<Object> values = new ArrayList<>(Collections.nCopies(AdminGroupQueueObserver.SAMPLE_LIMIT, "700"));
        values.add(TEAM_ID.toString());
        sample((long) values.size(), values);

        var result = observer.observe(GTeamSize.TWO, TEAM_ID);

        assertThat(result.presence()).isEqualTo("OUTSIDE_SAMPLE");
        assertThat(result.truncated()).isTrue();
        assertThat(result.sampledCount()).isEqualTo(AdminGroupQueueObserver.SAMPLE_LIMIT);
        assertThat(result.occurrences()).isZero();
        assertThat(result.firstPosition()).isNull();
    }

    @Test
    void independentlyObservedLengthCanIndicateTruncationEvenWhenRangeShrank() {
        sample(1_501L, List.of("700", "701"));

        var result = observer.observe(GTeamSize.TWO, TEAM_ID);

        assertThat(result.presence()).isEqualTo("OUTSIDE_SAMPLE");
        assertThat(result.truncated()).isTrue();
        assertThat(result.sampledCount()).isEqualTo(2);
        assertThat(result.length()).isEqualTo(1_501L);
    }

    @Test
    void selectedTeamInTruncatedSample_isFoundWithoutClaimingCompleteCoverage() {
        sample(1_501L, List.of("42", "700"));

        var result = observer.observe(GTeamSize.TWO, TEAM_ID);

        assertThat(result.presence()).isEqualTo("FOUND");
        assertThat(result.truncated()).isTrue();
        assertThat(result.occurrences()).isEqualTo(1);
    }

    @Test
    void nullLengthOrRange_isUnavailableNotEmpty() {
        sample(null, List.of());
        assertUnavailable(observer.observe(GTeamSize.TWO, TEAM_ID));
    }

    @Test
    void nullRange_isUnavailableNotEmpty() {
        sample(0L, null);
        assertUnavailable(observer.observe(GTeamSize.TWO, TEAM_ID));
    }

    @Test
    void sizeFailure_isUnavailableAndDoesNotAttemptAnotherCommand() {
        when(lists.size(key(GTeamSize.TWO)))
                .thenThrow(new RedisConnectionFailureException("synthetic-address-and-credential-marker"));

        var result = observer.observe(GTeamSize.TWO, TEAM_ID);

        assertUnavailable(result);
        assertThat(result.toString()).doesNotContain("synthetic-address-and-credential-marker");
        verify(lists, never()).range(anyString(), anyLong(), anyLong());
    }

    @Test
    void rangeFailure_discardsPartialLengthAndDoesNotPretendTheQueueIsEmpty() {
        when(lists.size(key(GTeamSize.TWO))).thenReturn(10L);
        when(lists.range(key(GTeamSize.TWO), 0, AdminGroupQueueObserver.SAMPLE_LIMIT))
                .thenThrow(new IllegalStateException("synthetic WRONGTYPE marker"));

        assertUnavailable(observer.observe(GTeamSize.TWO, TEAM_ID));
    }

    @ParameterizedTest
    @EnumSource(GTeamSize.class)
    void batchSharesOneReadAndObservationTimeButKeepsEachTeamsHitsSeparate(GTeamSize size) {
        String queueKey = key(size);
        when(lists.size(queueKey)).thenReturn(5L);
        when(lists.range(queueKey, 0, AdminGroupQueueObserver.SAMPLE_LIMIT))
                .thenReturn(List.of("42", "900", "43", "42", "invalid-fixture"));

        var result = observer.observeMany(size, List.of(42L, 43L, 44L));

        assertThat(result).containsOnlyKeys(42L, 43L, 44L);
        assertThat(result.get(42L).presence()).isEqualTo("FOUND");
        assertThat(result.get(42L).occurrences()).isEqualTo(2);
        assertThat(result.get(42L).firstPosition()).isEqualTo(1);
        assertThat(result.get(43L).presence()).isEqualTo("FOUND");
        assertThat(result.get(43L).occurrences()).isEqualTo(1);
        assertThat(result.get(43L).firstPosition()).isEqualTo(3);
        assertThat(result.get(44L).presence()).isEqualTo("NOT_OBSERVED");
        assertThat(result.get(44L).occurrences()).isZero();
        assertThat(result.get(44L).firstPosition()).isNull();
        assertThat(result.values()).allSatisfy(value -> {
            assertThat(value.length()).isEqualTo(5);
            assertThat(value.sampledCount()).isEqualTo(5);
            assertThat(value.invalidCount()).isEqualTo(1);
            assertThat(value.observedAt()).isEqualTo(result.get(42L).observedAt());
        });
        verify(lists, times(1)).size(queueKey);
        verify(lists, times(1)).range(queueKey, 0, AdminGroupQueueObserver.SAMPLE_LIMIT);
    }

    @Test
    void emptyBatchNeverAccessesRedis() {
        emptyBatch = true;

        assertThat(observer.observeMany(GTeamSize.TWO, List.of())).isEmpty();

        verifyNoInteractions(redis, lists);
    }

    @Test
    void duplicateRequestedIdsDoNotRepeatReadsOrMultiplyQueueOccurrences() {
        sample(3L, List.of("42", "43", "42"));

        var result = observer.observeMany(GTeamSize.TWO, List.of(42L, 42L, 43L));

        assertThat(result).containsOnlyKeys(42L, 43L);
        assertThat(result.get(42L).occurrences()).isEqualTo(2);
        assertThat(result.get(43L).occurrences()).isEqualTo(1);
        verify(lists).size(key(GTeamSize.TWO));
        verify(lists).range(key(GTeamSize.TWO), 0, AdminGroupQueueObserver.SAMPLE_LIMIT);
    }

    @Test
    void batchTruncationDistinguishesFoundFromUnobservedBeyondTheSample() {
        List<Object> values = new ArrayList<>(Collections.nCopies(AdminGroupQueueObserver.SAMPLE_LIMIT, "900"));
        values.set(0, "42");
        values.add("43");
        sample((long) values.size(), values);

        var result = observer.observeMany(GTeamSize.TWO, List.of(42L, 43L, 44L));

        assertThat(result.get(42L).presence()).isEqualTo("FOUND");
        assertThat(result.get(43L).presence()).isEqualTo("OUTSIDE_SAMPLE");
        assertThat(result.get(44L).presence()).isEqualTo("OUTSIDE_SAMPLE");
        assertThat(result.values()).allSatisfy(value -> {
            assertThat(value.truncated()).isTrue();
            assertThat(value.sampledCount()).isEqualTo(AdminGroupQueueObserver.SAMPLE_LIMIT);
        });
    }

    enum MissingBatchValue { LENGTH, RANGE }

    @ParameterizedTest
    @EnumSource(MissingBatchValue.class)
    void nullBatchReadMarksEveryRequestedTeamUnavailable(MissingBatchValue missing) {
        sample(missing == MissingBatchValue.LENGTH ? null : 1L,
                missing == MissingBatchValue.RANGE ? null : List.of("42"));

        var result = observer.observeMany(GTeamSize.TWO, List.of(42L, 43L));

        assertThat(result).containsOnlyKeys(42L, 43L);
        result.values().forEach(this::assertUnavailable);
        assertThat(result.get(42L).observedAt()).isEqualTo(result.get(43L).observedAt());
    }

    enum BatchFailure { LENGTH, RANGE }

    @ParameterizedTest
    @EnumSource(BatchFailure.class)
    void batchFailureNeverReturnsPartialResultsOrExceptionDetails(BatchFailure failure) {
        if (failure == BatchFailure.LENGTH) {
            when(lists.size(key(GTeamSize.TWO))).thenThrow(
                    new RedisConnectionFailureException("synthetic-private-endpoint-marker"));
        } else {
            when(lists.size(key(GTeamSize.TWO))).thenReturn(4L);
            when(lists.range(key(GTeamSize.TWO), 0, AdminGroupQueueObserver.SAMPLE_LIMIT))
                    .thenThrow(new IllegalStateException("synthetic-private-endpoint-marker"));
        }

        var result = observer.observeMany(GTeamSize.TWO, List.of(42L, 43L));

        assertThat(result).containsOnlyKeys(42L, 43L);
        result.values().forEach(this::assertUnavailable);
        assertThat(result.toString()).doesNotContain("synthetic-private-endpoint-marker");
        if (failure == BatchFailure.LENGTH) verify(lists, never()).range(anyString(), anyLong(), anyLong());
    }

    private void sample(Long length, List<Object> values) {
        when(lists.size(key(GTeamSize.TWO))).thenReturn(length);
        when(lists.range(key(GTeamSize.TWO), 0, AdminGroupQueueObserver.SAMPLE_LIMIT)).thenReturn(values);
    }

    private void assertUnavailable(AdminGroupMatchingDtos.Queue result) {
        assertThat(result.presence()).isEqualTo("UNAVAILABLE");
        assertThat(result.length()).isNull();
        assertThat(result.sampledCount()).isZero();
        assertThat(result.firstPosition()).isNull();
        assertThat(result.occurrences()).isZero();
        assertThat(result.invalidCount()).isZero();
        assertThat(result.observedAt()).isNotNull();
    }

    private String key(GTeamSize size) {
        return "matching:queue:" + size.name();
    }
}
