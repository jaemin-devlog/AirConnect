package univ.airconnect.statistics.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import univ.airconnect.department.repository.DepartmentRankingProjection;
import univ.airconnect.department.repository.DepartmentRepository;
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
    private DepartmentRepository departmentRepository;

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
    void getRealtimeMainStatistics_returnsLightweightCountsAndTopDepartment() {
        DepartmentRankingProjection top = departmentCount(
                1L, "시각디자인학과", "디자인융합학부", "ACTIVE", 56L);
        when(userRepository.countRegisteredUsersExcludingDeleted()).thenReturn(135L);
        when(matchingConnectionRepository.countByStatus(ConnectionStatus.ACCEPTED)).thenReturn(40L);
        when(finalGroupChatRoomRepository.countByStatusIn(any())).thenReturn(10L);
        when(stompSessionRegistry.onlineUserCount()).thenReturn(17);
        when(departmentRepository.findTopRankedByMatchingRequests()).thenReturn(java.util.Optional.of(top));

        RealtimeMainStatisticsResponse response = statisticsService.getRealtimeMainStatistics();

        assertThat(response.totalRegisteredUsers()).isEqualTo(135L);
        assertThat(response.totalMatchSuccessCount()).isEqualTo(50L);
        assertThat(response.onlineUserCount()).isEqualTo(17);
        assertThat(response.topDepartment().deptName()).isEqualTo("시각디자인학과");
        assertThat(response.topDepartment().requestCount()).isEqualTo(56L);
        assertThat(response.updatedAt()).isNotNull();
    }

    @Test
    void getDepartmentRankings_returnsAllDepartmentsWithCompetitionRanks() {
        when(departmentRepository.findAllRankedByMatchingRequests()).thenReturn(List.of(
                departmentCount(1L, "항공운항학과", "항공학부", "ACTIVE", 23L),
                departmentCount(2L, "간호학과", "보건학부", "ACTIVE", 23L),
                departmentCount(3L, "항공컴퓨터학과", "항공융합학부(이전)", "LEGACY", 0L)
        ));

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

    private DepartmentRankingProjection departmentCount(Long departmentId, String deptName,
                                                        String collegeName, String status,
                                                        long requestCount) {
        return new DepartmentRankingProjection() {
            @Override
            public Long getDepartmentId() {
                return departmentId;
            }

            @Override
            public String getDeptName() {
                return deptName;
            }

            @Override
            public String getCollegeName() {
                return collegeName;
            }

            @Override
            public String getStatus() {
                return status;
            }

            @Override
            public long getRequestCount() {
                return requestCount;
            }
        };
    }
}
