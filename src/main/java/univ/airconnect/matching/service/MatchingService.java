package univ.airconnect.matching.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import univ.airconnect.chat.domain.ChatRoomType;
import univ.airconnect.chat.dto.response.ChatRoomResponse;
import univ.airconnect.chat.service.ChatService;
import univ.airconnect.matching.domain.ConnectionStatus;
import univ.airconnect.matching.domain.entity.MatchingConnection;
import univ.airconnect.matching.dto.response.*;
import univ.airconnect.matching.exception.MatchingErrorCode;
import univ.airconnect.matching.exception.MatchingException;
import univ.airconnect.matching.repository.MatchingConnectionRepository;
import univ.airconnect.matching.repository.MatchingExposureRepository;
import univ.airconnect.matching.repository.MatchingQueueEntryRepository;
import univ.airconnect.user.domain.UserStatus;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.domain.entity.UserProfile;
import univ.airconnect.user.dto.response.UserMeResponse;
import univ.airconnect.user.dto.response.UserProfileResponse;
import univ.airconnect.user.repository.UserProfileRepository;
import univ.airconnect.user.repository.UserRepository;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MatchingService {

    private final MatchingQueueEntryRepository matchingQueueEntryRepository;
    private final MatchingExposureRepository matchingExposureRepository;
    private final MatchingConnectionRepository matchingConnectionRepository;
    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final ChatService chatService;

    @Value("${app.upload.profile-image-url-base:http://localhost:8080/api/v1/users/profile-images}")
    private String imageUrlBase;

    @Transactional
    public MatchingRecommendationResponse recommend(Long userId) {
        validateActiveUser(userId);
        requireProfileGender(userId);

        // 티켓 검증 (차감은 나중에)
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new MatchingException(MatchingErrorCode.USER_NOT_FOUND));
        
        if (user.getTickets() < 1) {
            log.warn("⚠️ 티켓 부족: userId={}, 현재 티켓={}", userId, user.getTickets());
            throw new MatchingException(MatchingErrorCode.INSUFFICIENT_TICKETS);
        }

        // 현재 조건에 맞는 후보 전체를 조회한다.
        List<User> selectedUsers = userRepository.findActiveUsersWithProfileForMatching(userId);

        if (selectedUsers.isEmpty()) {
            log.warn("⚠️ 추천 대상 없음: userId={}, 티켓 소진 안 함", userId);
            matchingExposureRepository.deleteByUserId(userId);
            return MatchingRecommendationResponse.builder()
                    .count(0)
                    .candidates(Collections.emptyList())
                    .userTicketsRemaining(user.getTickets())
                    .build();
        }

        // 새로고침 시 최신 후보 전체가 연결 가능하도록 노출 이력을 재구성한다.
        matchingExposureRepository.deleteByUserId(userId);
        List<Long> candidateIds = selectedUsers.stream()
                .map(User::getId)
                .distinct()
                .toList();
        for (Long candidateId : candidateIds) {
            matchingExposureRepository.insertIgnore(userId, candidateId);
        }

        log.info("✅ 추천 완료: userId={}, 추천 대상 {}명", userId, selectedUsers.size());

        List<UserMeResponse> candidates = selectedUsers.stream()
                .map(this::toUserMeResponse)
                .toList();

        // ✅ 추천 대상이 있을 때만 티켓 차감
        user.consumeTickets(1);
        log.info("🎫 매칭 티켓 사용: userId={}, 사용한 티켓=1, 남은 티켓={}", userId, user.getTickets());

        return MatchingRecommendationResponse.builder()
                .count(candidates.size())
                .candidates(candidates)
                .userTicketsRemaining(user.getTickets())
                .build();
    }

    @Transactional
    public MatchingConnectResponse connect(Long userId, Long targetUserId) {
        log.info("📨 매칭 요청 시작: requester={}, target={}", userId, targetUserId);
        
        if (Objects.equals(userId, targetUserId)) {
            log.warn("⚠️ 자신에게 요청 불가: userId={}", userId);
            throw new MatchingException(MatchingErrorCode.INVALID_TARGET);
        }

        validateActiveUser(userId);
        validateActiveUser(targetUserId);

        if (!matchingExposureRepository.existsByUserIdAndCandidateUserId(userId, targetUserId)) {
            log.warn("⚠️ 노출되지 않은 사용자: userId={}, targetUserId={}", userId, targetUserId);
            throw new MatchingException(MatchingErrorCode.CANDIDATE_NOT_EXPOSED);
        }

        // 티켓 검증 (차감은 나중에)
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new MatchingException(MatchingErrorCode.USER_NOT_FOUND));
        
        if (user.getTickets() < 2) {
            log.warn("⚠️ 티켓 부족: userId={}, 현재 티켓={}", userId, user.getTickets());
            throw new MatchingException(MatchingErrorCode.INSUFFICIENT_TICKETS);
        }

        Long user1 = Math.min(userId, targetUserId);
        Long user2 = Math.max(userId, targetUserId);

        Optional<MatchingConnection> existing = matchingConnectionRepository.findByUser1IdAndUser2Id(user1, user2);
        if (existing.isPresent()) {
            MatchingConnection connection = existing.get();
            
            // ACCEPTED: 이미 연결됨
            if (connection.getStatus() == ConnectionStatus.ACCEPTED) {
                log.info("ℹ️ 이미 수락된 연결: userId={}, targetUserId={}, chatRoomId={}", 
                        userId, targetUserId, connection.getChatRoomId());
                return new MatchingConnectResponse(connection.getChatRoomId(), targetUserId, true);
            }
            
            // PENDING: 이미 요청이 있음
            if (connection.getStatus() == ConnectionStatus.PENDING) {
                log.warn("⚠️ 이미 요청 중인 연결: userId={}, targetUserId={}, connectionId={}", 
                        userId, targetUserId, connection.getId());
                throw new MatchingException(MatchingErrorCode.ALREADY_CONNECTED);
            }
            
            // REJECTED: 기존 거절된 요청을 다시 활성화
            if (connection.getStatus() == ConnectionStatus.REJECTED) {
                log.info("🔄 거절된 요청 재시도: userId={}, targetUserId={}, connectionId={}", 
                        userId, targetUserId, connection.getId());
                // REJECTED 상태를 PENDING으로 변경
                connection.resetToPending();
                
                // ✅ 모든 검증 완료 후 티켓 차감
                user.consumeTickets(2);
                log.info("🎫 컨택 티켓 사용: userId={}, 사용한 티켓=2, 남은 티켓={}", userId, user.getTickets());
                
                return new MatchingConnectResponse(null, targetUserId, false);
            }
        }

        // 새로운 요청 생성 (PENDING 상태로 저장)
        MatchingConnection connection = matchingConnectionRepository.save(
                MatchingConnection.createPending(userId, targetUserId)
        );

        // ✅ 모든 검증과 로직이 성공한 후에만 티켓 차감
        user.consumeTickets(2);
        log.info("🎫 컨택 티켓 사용: userId={}, 사용한 티켓=2, 남은 티켓={}", userId, user.getTickets());

        log.info("✅ 매칭 요청 생성 완료: requester={}, target={}, connectionId={}", userId, targetUserId, connection.getId());

        return new MatchingConnectResponse(null, targetUserId, false);
    }

    @Transactional
    public MatchingRequestsResponse getRequests(Long userId) {
        validateActiveUser(userId);

        List<MatchingConnection> sentConnections = matchingConnectionRepository
                .findByRequesterIdAndStatus(userId, ConnectionStatus.PENDING);
        List<MatchingConnection> receivedConnections = matchingConnectionRepository
                .findReceivedRequestsByStatus(userId, ConnectionStatus.PENDING);

        log.info("📬 요청 목록 조회: userId={}, 보낸요청={}건, 받은요청={}건", 
                userId, sentConnections.size(), receivedConnections.size());

        List<MatchingRequestResponse> sent = sentConnections.stream()
                .map(conn -> {
                    MatchingRequestResponse response = toMatchingRequestResponse(conn, conn.getOtherUserId(userId));
                    log.debug("  📤 보낸요청: connectionId={}, targetUserId={}, targetName={}", 
                            conn.getId(), response.getUserId(), response.getNickname());
                    return response;
                })
                .toList();

        List<MatchingRequestResponse> received = receivedConnections.stream()
                .map(conn -> {
                    MatchingRequestResponse response = toMatchingRequestResponse(conn, conn.getRequesterId());
                    log.debug("  📥 받은요청: connectionId={}, requesterId={}, requesterName={}", 
                            conn.getId(), response.getUserId(), response.getNickname());
                    return response;
                })
                .toList();

        return MatchingRequestsResponse.builder()
                .sentCount(sent.size())
                .receivedCount(received.size())
                .sent(sent)
                .received(received)
                .build();
    }

    private MatchingRequestResponse toMatchingRequestResponse(MatchingConnection conn, Long otherUserId) {
        User otherUser = userRepository.findById(otherUserId)
                .orElseThrow(() -> new MatchingException(MatchingErrorCode.USER_NOT_FOUND));
        UserProfile profile = otherUser.getUserProfile();

        return MatchingRequestResponse.builder()
                .connectionId(conn.getId())
                .userId(otherUserId)
                .nickname(otherUser.getNickname())
                .deptName(otherUser.getDeptName())
                .studentNum(otherUser.getStudentNum())
                .intro(profile != null ? profile.getIntro() : null)
                .mbti(profile != null ? profile.getMbti() : null)
                .residence(profile != null ? profile.getResidence() : null)
                .profileImagePath(profile != null && profile.getProfileImagePath() != null
                        ? toFullImageUrl(profile.getProfileImagePath())
                        : null)
                .status(conn.getStatus())
                .requestedAt(conn.getConnectedAt())
                .respondedAt(conn.getRespondedAt())
                .build();
    }

    @Transactional
    public MatchingResponseResponse acceptRequest(Long userId, Long connectionId) {
        validateActiveUser(userId);

        log.info("💬 요청 수락 시작: userId={}, connectionId={}", userId, connectionId);

        MatchingConnection connection = matchingConnectionRepository.findById(connectionId)
                .orElseThrow(() -> new MatchingException(MatchingErrorCode.CONNECTION_NOT_FOUND));

        // userId가 요청 받은 사람인지 확인
        Long otherUserId = connection.getOtherUserId(userId);
        if (Objects.equals(userId, connection.getRequesterId())) {
            log.warn("⚠️ 요청 수락 권한 없음: userId={}, requesterId={}", userId, connection.getRequesterId());
            throw new MatchingException(MatchingErrorCode.INVALID_REQUEST);
        }

        if (connection.getStatus() != ConnectionStatus.PENDING) {
            log.warn("⚠️ 요청 상태 불일치: connectionId={}, status={}", connectionId, connection.getStatus());
            throw new MatchingException(MatchingErrorCode.INVALID_REQUEST);
        }

        // 채팅방 생성
        ChatRoomResponse room = chatService.createChatRoom("소개팅 1:1", ChatRoomType.PERSONAL, userId, otherUserId);
        log.debug("💌 채팅방 생성됨: chatRoomId={}", room.getId());

        // 연결 수락
        connection.accept(room.getId());

        log.info("✅ 매칭 요청 수락 완료: 수락자userId={}, 요청자userId={}, connectionId={}, chatRoomId={}", 
                userId, connection.getRequesterId(), connectionId, room.getId());

        return MatchingResponseResponse.builder()
                .connectionId(connection.getId())
                .targetUserId(otherUserId)
                .chatRoomId(room.getId())
                .status(connection.getStatus())
                .build();
    }

    @Transactional
    public MatchingResponseResponse rejectRequest(Long userId, Long connectionId) {
        validateActiveUser(userId);

        log.info("❌ 요청 거절 시작: userId={}, connectionId={}", userId, connectionId);

        MatchingConnection connection = matchingConnectionRepository.findById(connectionId)
                .orElseThrow(() -> new MatchingException(MatchingErrorCode.CONNECTION_NOT_FOUND));

        // userId가 요청 받은 사람인지 확인
        if (Objects.equals(userId, connection.getRequesterId())) {
            log.warn("⚠️ 요청 거절 권한 없음: userId={}, requesterId={}", userId, connection.getRequesterId());
            throw new MatchingException(MatchingErrorCode.INVALID_REQUEST);
        }

        if (connection.getStatus() != ConnectionStatus.PENDING) {
            log.warn("⚠️ 요청 상태 불일치: connectionId={}, status={}", connectionId, connection.getStatus());
            throw new MatchingException(MatchingErrorCode.INVALID_REQUEST);
        }

        Long otherUserId = connection.getOtherUserId(userId);
        
        // 연결 거절
        connection.reject();

        log.info("✅ 매칭 요청 거절 완료: 거절자userId={}, 요청자userId={}, connectionId={}", 
                userId, connection.getRequesterId(), connectionId);

        return MatchingResponseResponse.builder()
                .connectionId(connection.getId())
                .targetUserId(otherUserId)
                .chatRoomId(null)
                .status(connection.getStatus())
                .build();
    }

    private UserMeResponse toUserMeResponse(User user) {
        UserProfile profile = user.getUserProfile();
        
        UserProfileResponse profileResponse = null;
        if (profile != null) {
            profileResponse = UserProfileResponse.from(profile, imageUrlBase);
        }

        return UserMeResponse.builder()
                .userId(user.getId())
                .provider(user.getProvider())
                .socialId(user.getSocialId())
                .email(user.getEmail())
                .name(user.getName())
                .deptName(user.getDeptName())
                .nickname(user.getNickname())
                .studentNum(user.getStudentNum())
                .status(user.getStatus())
                .onboardingStatus(user.getOnboardingStatus())
                .profileExists(profile != null)
                .profile(profileResponse)
                .build();
    }

    private String toFullImageUrl(String profileImagePath) {
        if (profileImagePath == null || profileImagePath.isBlank()) {
            return null;
        }
        if (profileImagePath.startsWith("http://") || profileImagePath.startsWith("https://")) {
            return profileImagePath;
        }
        return imageUrlBase + "/" + profileImagePath;
    }

    private void validateActiveUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new MatchingException(MatchingErrorCode.USER_NOT_FOUND));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new MatchingException(MatchingErrorCode.INVALID_TARGET);
        }
    }

    private void requireProfileGender(Long userId) {
        UserProfile profile = userProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new MatchingException(MatchingErrorCode.PROFILE_REQUIRED));

        if (profile.getGender() == null) {
            throw new MatchingException(MatchingErrorCode.PROFILE_GENDER_REQUIRED);
        }
    }

}

