package univ.airconnect.statistics.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import univ.airconnect.groupmatching.domain.GFinalGroupRoomStatus;
import univ.airconnect.groupmatching.repository.GFinalGroupChatRoomRepository;
import univ.airconnect.global.security.stomp.StompSessionRegistry;
import univ.airconnect.matching.domain.ConnectionStatus;
import univ.airconnect.matching.repository.MatchingConnectionRepository;
import univ.airconnect.statistics.dto.response.MainStatisticsResponse;
import univ.airconnect.statistics.dto.response.DepartmentRankingResponse;
import univ.airconnect.statistics.dto.response.RealtimeMainStatisticsResponse;
import univ.airconnect.statistics.repository.GenderCountProjection;
import univ.airconnect.user.domain.Gender;
import univ.airconnect.user.repository.UserProfileRepository;
import univ.airconnect.user.repository.UserRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatisticsServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private MatchingConnectionRepository matchingConnectionRepository;

    @Mock
    private GFinalGroupChatRoomRepository finalGroupChatRoomRepository;

    @Mock
    private DepartmentRankingSnapshotService departmentRankingSnapshotService;

    @Mock
    private StompSessionRegistry stompSessionRegistry;

    @InjectMocks
    private StatisticsService statisticsService;

    @Test
    void getMainStatistics_returnsAggregatedValues() {
        when(userRepository.countRegisteredUsersExcludingDeleted()).thenReturn(135L);
        when(userRepository.countActiveSignedUpUsers()).thenReturn(120L);
        when(userRepository.countDailyActiveSignedUpUsers(any())).thenReturn(34L);
        when(userProfileRepository.countActiveSignedUpUsersByGender()).thenReturn(List.of(
                genderCount(Gender.MALE, 70L),
                genderCount(Gender.FEMALE, 50L)
        ));
        when(matchingConnectionRepository.countByStatus(ConnectionStatus.ACCEPTED)).thenReturn(40L);
        when(finalGroupChatRoomRepository.countByStatusIn(any())).thenReturn(10L);
        when(stompSessionRegistry.onlineUserCount()).thenReturn(17);
        MainStatisticsResponse response = statisticsService.getMainStatistics();

        assertThat(response.getTotalRegisteredUsers()).isEqualTo(135L);
        assertThat(response.getDailyActiveUsers()).isEqualTo(34L);
        assertThat(response.getOnlineUserCount()).isEqualTo(17);
        assertThat(response.getGenderRatio().getMaleUsers()).isEqualTo(70L);
        assertThat(response.getGenderRatio().getFemaleUsers()).isEqualTo(50L);
        assertThat(response.getGenderRatio().getUnknownUsers()).isZero();
        assertThat(response.getGenderRatio().getMalePercentage()).isEqualTo(58);
        assertThat(response.getGenderRatio().getFemalePercentage()).isEqualTo(42);
        assertThat(response.getTotalMatchSuccessCount()).isEqualTo(50L);
        assertThat(response.getGeneratedAt()).isNotNull();
    }

    @Test
    void getRealtimeMainStatistics_returnsLightweightCountsAndTopThreeDepartments() {
        when(userRepository.countRegisteredUsersExcludingDeleted()).thenReturn(135L);
        when(userRepository.countLast24HoursActiveUsersExcludingDeleted(any())).thenReturn(42L);
        when(matchingConnectionRepository.countByStatus(ConnectionStatus.ACCEPTED)).thenReturn(40L);
        when(finalGroupChatRoomRepository.countByStatusIn(any())).thenReturn(10L);
        when(departmentRankingSnapshotService.getTopThree()).thenReturn(List.of(
                new RealtimeMainStatisticsResponse.TopDepartment(1, 1L, "시각디자인학과", 56L),
                new RealtimeMainStatisticsResponse.TopDepartment(2, 2L, "항공운항학과", 41L),
                new RealtimeMainStatisticsResponse.TopDepartment(3, 3L, "간호학과", 32L)
        ));

        RealtimeMainStatisticsResponse response = statisticsService.getRealtimeMainStatistics();

        assertThat(response.totalRegisteredUsers()).isEqualTo(135L);
        assertThat(response.totalMatchSuccessCount()).isEqualTo(50L);
        assertThat(response.last24HoursActiveUserCount()).isEqualTo(42L);
        assertThat(response.topDepartments()).hasSize(3);
        assertThat(response.topDepartments().get(0).rank()).isEqualTo(1);
        assertThat(response.topDepartments().get(0).deptName()).isEqualTo("시각디자인학과");
        assertThat(response.topDepartments().get(0).requestCount()).isEqualTo(56L);
        assertThat(response.topDepartments().get(2).rank()).isEqualTo(3);
        assertThat(response.updatedAt()).isNotNull();
    }

    @Test
    void getDepartmentRankings_returnsAllDepartmentsWithCompetitionRanks() {
        List<DepartmentRankingResponse> expected = List.of(
                DepartmentRankingResponse.builder().rank(1).departmentId(1L)
                        .deptName("항공운항학과").requestCount(23L).build(),
                DepartmentRankingResponse.builder().rank(1).departmentId(2L)
                        .deptName("간호학과").requestCount(23L).build(),
                DepartmentRankingResponse.builder().rank(3).departmentId(3L)
                        .deptName("항공컴퓨터학과").requestCount(0L).build()
        );
        when(departmentRankingSnapshotService.getRankings()).thenReturn(expected);

        List<DepartmentRankingResponse> rankings = statisticsService.getDepartmentRankings();

        assertThat(rankings).hasSize(3);
        assertThat(rankings.get(0).getRank()).isEqualTo(1);
        assertThat(rankings.get(1).getRank()).isEqualTo(1);
        assertThat(rankings.get(2).getRank()).isEqualTo(3);
        assertThat(rankings.get(2).getRequestCount()).isZero();
    }

    private GenderCountProjection genderCount(Gender gender, long count) {
        return new GenderCountProjection() {
            @Override
            public Gender getGender() {
                return gender;
            }

            @Override
            public long getCount() {
                return count;
            }
        };
    }

}
