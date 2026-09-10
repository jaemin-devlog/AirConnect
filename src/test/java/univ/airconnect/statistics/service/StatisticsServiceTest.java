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
import univ.airconnect.matching.domain.ConnectionStatus;
import univ.airconnect.matching.repository.MatchingConnectionRepository;
import univ.airconnect.statistics.dto.response.MainStatisticsResponse;
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

    @InjectMocks
    private StatisticsService statisticsService;

    @Test
    void getMainStatistics_returnsAggregatedValues() {
        when(userRepository.countActiveSignedUpUsers()).thenReturn(120L);
        when(userRepository.countDailyActiveSignedUpUsers(any())).thenReturn(34L);
        when(userProfileRepository.countActiveSignedUpUsersByGender()).thenReturn(List.of(
                genderCount(Gender.MALE, 70L),
                genderCount(Gender.FEMALE, 50L)
        ));
        when(matchingConnectionRepository.countByStatus(ConnectionStatus.ACCEPTED)).thenReturn(40L);
        when(finalGroupChatRoomRepository.countByStatusIn(any())).thenReturn(10L);
        when(departmentRepository.findAllRankedByMatchingRequests()).thenReturn(List.of(
                departmentCount(1L, "항공운항학과", "항공학부", "ACTIVE", 23L),
                departmentCount(2L, "간호학과", "보건학부", "ACTIVE", 23L),
                departmentCount(3L, "항공컴퓨터학과", "항공융합학부(이전)", "LEGACY", 0L)
        ));

        MainStatisticsResponse response = statisticsService.getMainStatistics();

        assertThat(response.getTotalRegisteredUsers()).isEqualTo(120L);
        assertThat(response.getDailyActiveUsers()).isEqualTo(34L);
        assertThat(response.getGenderRatio().getMaleUsers()).isEqualTo(70L);
        assertThat(response.getGenderRatio().getFemaleUsers()).isEqualTo(50L);
        assertThat(response.getGenderRatio().getUnknownUsers()).isZero();
        assertThat(response.getGenderRatio().getMalePercentage()).isEqualTo(58);
        assertThat(response.getGenderRatio().getFemalePercentage()).isEqualTo(42);
        assertThat(response.getTotalMatchSuccessCount()).isEqualTo(50L);
        assertThat(response.getTopRequestedDepartments()).hasSize(3);
        assertThat(response.getTopRequestedDepartments().get(0).getRank()).isEqualTo(1);
        assertThat(response.getTopRequestedDepartments().get(0).getDeptName()).isEqualTo("항공운항학과");
        assertThat(response.getTopRequestedDepartments().get(0).getRequestCount()).isEqualTo(23L);
        assertThat(response.getTopRequestedDepartments().get(1).getRank()).isEqualTo(1);
        assertThat(response.getTopRequestedDepartments().get(2).getRank()).isEqualTo(3);
        assertThat(response.getTopRequestedDepartments().get(2).getRequestCount()).isZero();
        assertThat(response.getGeneratedAt()).isNotNull();
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
