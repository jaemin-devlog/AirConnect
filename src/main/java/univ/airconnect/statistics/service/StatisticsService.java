package univ.airconnect.statistics.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import univ.airconnect.department.repository.DepartmentRankingProjection;
import univ.airconnect.department.repository.DepartmentRepository;
import univ.airconnect.groupmatching.domain.GFinalGroupRoomStatus;
import univ.airconnect.groupmatching.repository.GFinalGroupChatRoomRepository;
import univ.airconnect.global.security.stomp.StompSessionRegistry;
import univ.airconnect.matching.domain.ConnectionStatus;
import univ.airconnect.matching.repository.MatchingConnectionRepository;
import univ.airconnect.statistics.dto.response.MainStatisticsResponse;
import univ.airconnect.statistics.dto.response.DepartmentRankingResponse;
import univ.airconnect.statistics.dto.response.OnlinePresenceResponse;
import univ.airconnect.statistics.dto.response.RealtimeMainStatisticsResponse;
import univ.airconnect.statistics.repository.GenderCountProjection;
import univ.airconnect.user.domain.Gender;
import univ.airconnect.user.repository.UserProfileRepository;
import univ.airconnect.user.repository.UserRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StatisticsService {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final MatchingConnectionRepository matchingConnectionRepository;
    private final GFinalGroupChatRoomRepository finalGroupChatRoomRepository;
    private final DepartmentRepository departmentRepository;
    private final StompSessionRegistry stompSessionRegistry;

    public MainStatisticsResponse getMainStatistics() {
        long totalRegisteredUsers = userRepository.countRegisteredUsersExcludingDeleted();
        long activeSignedUpUsers = userRepository.countActiveSignedUpUsers();
        long dailyActiveUsers = userRepository.countDailyActiveSignedUpUsers(LocalDate.now().atStartOfDay());

        MainStatisticsResponse.GenderRatio genderRatio = buildGenderRatio(activeSignedUpUsers);

        long oneToOneSuccessCount = matchingConnectionRepository.countByStatus(ConnectionStatus.ACCEPTED);
        long groupSuccessCount = finalGroupChatRoomRepository.countByStatusIn(
                EnumSet.of(GFinalGroupRoomStatus.ACTIVE, GFinalGroupRoomStatus.ENDED)
        );

        return MainStatisticsResponse.builder()
                .totalRegisteredUsers(totalRegisteredUsers)
                .dailyActiveUsers(dailyActiveUsers)
                .onlineUserCount(stompSessionRegistry.onlineUserCount())
                .genderRatio(genderRatio)
                .totalMatchSuccessCount(oneToOneSuccessCount + groupSuccessCount)
                .generatedAt(LocalDateTime.now())
                .build();
    }

    public RealtimeMainStatisticsResponse getRealtimeMainStatistics() {
        long totalRegisteredUsers = userRepository.countRegisteredUsersExcludingDeleted();
        long totalMatchSuccessCount = countTotalMatchSuccesses();
        RealtimeMainStatisticsResponse.TopDepartment topDepartment = departmentRepository
                .findTopRankedByMatchingRequests()
                .map(projection -> new RealtimeMainStatisticsResponse.TopDepartment(
                        projection.getDepartmentId(),
                        projection.getDeptName(),
                        projection.getRequestCount()
                ))
                .orElse(null);

        return new RealtimeMainStatisticsResponse(
                totalRegisteredUsers,
                totalMatchSuccessCount,
                stompSessionRegistry.onlineUserCount(),
                topDepartment,
                LocalDateTime.now()
        );
    }

    public List<DepartmentRankingResponse> getDepartmentRankings() {
        List<DepartmentRankingProjection> projections = departmentRepository.findAllRankedByMatchingRequests();
        List<DepartmentRankingResponse> rankings = new ArrayList<>(projections.size());
        int rank = 0;
        long previousCount = Long.MIN_VALUE;
        for (int i = 0; i < projections.size(); i++) {
            DepartmentRankingProjection projection = projections.get(i);
            if (projection.getRequestCount() != previousCount) {
                rank = i + 1;
                previousCount = projection.getRequestCount();
            }
            rankings.add(DepartmentRankingResponse.builder()
                    .rank(rank)
                    .departmentId(projection.getDepartmentId())
                    .deptName(projection.getDeptName())
                    .collegeName(projection.getCollegeName())
                    .status(projection.getStatus())
                    .requestCount(projection.getRequestCount())
                    .build());
        }
        return rankings;
    }

    public OnlinePresenceResponse getOnlinePresence() {
        return OnlinePresenceResponse.of(stompSessionRegistry.onlineUserCount());
    }

    private long countTotalMatchSuccesses() {
        long oneToOneSuccessCount = matchingConnectionRepository.countByStatus(ConnectionStatus.ACCEPTED);
        long groupSuccessCount = finalGroupChatRoomRepository.countByStatusIn(
                EnumSet.of(GFinalGroupRoomStatus.ACTIVE, GFinalGroupRoomStatus.ENDED)
        );
        return oneToOneSuccessCount + groupSuccessCount;
    }

    private MainStatisticsResponse.GenderRatio buildGenderRatio(long totalRegisteredUsers) {
        long maleUsers = 0L;
        long femaleUsers = 0L;

        for (GenderCountProjection projection : userProfileRepository.countActiveSignedUpUsersByGender()) {
            if (projection.getGender() == Gender.MALE) {
                maleUsers = projection.getCount();
            } else if (projection.getGender() == Gender.FEMALE) {
                femaleUsers = projection.getCount();
            }
        }

        long unknownUsers = Math.max(totalRegisteredUsers - maleUsers - femaleUsers, 0L);
        long knownGenderUsers = maleUsers + femaleUsers;
        int malePercentage = knownGenderUsers == 0 ? 0 : (int) Math.round((maleUsers * 100.0) / knownGenderUsers);
        int femalePercentage = knownGenderUsers == 0 ? 0 : (int) Math.round((femaleUsers * 100.0) / knownGenderUsers);

        return MainStatisticsResponse.GenderRatio.builder()
                .maleUsers(maleUsers)
                .femaleUsers(femaleUsers)
                .unknownUsers(unknownUsers)
                .malePercentage(malePercentage)
                .femalePercentage(femalePercentage)
                .build();
    }
}
