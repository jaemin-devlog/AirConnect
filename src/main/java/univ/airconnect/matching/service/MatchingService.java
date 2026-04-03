package univ.airconnect.matching.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import univ.airconnect.analytics.domain.AnalyticsEventType;
import univ.airconnect.analytics.service.AnalyticsService;
import univ.airconnect.chat.repository.ChatRoomMemberRepository;
import univ.airconnect.chat.dto.response.ChatRoomResponse;
import univ.airconnect.chat.service.ChatService;
import univ.airconnect.matching.domain.ConnectionStatus;
import univ.airconnect.matching.domain.entity.MatchingConnection;
import univ.airconnect.matching.domain.entity.MatchingExposure;
import univ.airconnect.matching.dto.response.*;
import univ.airconnect.matching.exception.MatchingErrorCode;
import univ.airconnect.matching.exception.MatchingException;
import univ.airconnect.matching.repository.MatchingConnectionRepository;
import univ.airconnect.matching.repository.MatchingExposureRepository;
import univ.airconnect.notification.domain.NotificationType;
import univ.airconnect.notification.service.NotificationService;
import univ.airconnect.user.domain.MilestoneType;
import univ.airconnect.user.domain.UserStatus;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.domain.entity.UserProfile;
import univ.airconnect.user.dto.response.UserProfileResponse;
import univ.airconnect.user.repository.UserMilestoneRepository;
import univ.airconnect.user.repository.UserProfileRepository;
import univ.airconnect.user.repository.UserRepository;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MatchingService {

    private static final int RECOMMENDATION_LIMIT = 2;

    private final MatchingExposureRepository matchingExposureRepository;
    private final MatchingConnectionRepository matchingConnectionRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final UserRepository userRepository;
    private final UserMilestoneRepository userMilestoneRepository;
    private final UserProfileRepository userProfileRepository;
    private final ChatService chatService;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;
    private final AnalyticsService analyticsService;

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

        List<User> allCandidates = userRepository.findActiveUsersWithProfileForMatching(userId);

        // 노출 이력을 사용해 새로고침 시 후보가 순환되도록 한다.
        Set<Long> exposedCandidateIds = new HashSet<>(
                matchingExposureRepository.findCandidateUserIdsByUserId(userId)
        );

        List<User> unseenCandidates = allCandidates.stream()
                .filter(candidate -> !exposedCandidateIds.contains(candidate.getId()))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));

        Collections.shuffle(unseenCandidates);

        List<User> selectedUsers = unseenCandidates.stream()
                .limit(RECOMMENDATION_LIMIT)
                .toList();

        // 이미 모든 후보가 소진된 경우 노출 이력을 리셋하고 처음부터 다시 순환한다.
        if (selectedUsers.isEmpty() && !allCandidates.isEmpty()) {
            matchingExposureRepository.deleteByUserId(userId);
            List<User> resetCandidates = new ArrayList<>(allCandidates);
            Collections.shuffle(resetCandidates);
            selectedUsers = resetCandidates.stream()
                    .limit(RECOMMENDATION_LIMIT)
                    .toList();
        }

        if (selectedUsers.isEmpty()) {
            log.warn("⚠️ 추천 대상 없음: userId={}, 티켓 소진 안 함", userId);
            matchingExposureRepository.deleteByUserId(userId);
            analyticsService.trackServerEvent(
                    AnalyticsEventType.MATCH_RECOMMENDATION_REFRESHED,
                    userId,
                    Map.of(
                            "candidateCount", 0,
                            "ticketsRemaining", user.getTickets()
                    )
            );
            return MatchingRecommendationResponse.builder()
                    .count(0)
                    .candidates(Collections.emptyList())
                    .userTicketsRemaining(user.getTickets())
                    .build();
        }

        // connect 검증용으로 이번에 응답된 후보를 노출 이력에 반영한다.
        List<Long> candidateIds = selectedUsers.stream()
                .map(User::getId)
                .distinct()
                .toList();
        for (Long candidateId : candidateIds) {
            saveExposureIfAbsent(userId, candidateId);
        }

        log.info("✅ 추천 완료: userId={}, 추천 대상 {}명", userId, selectedUsers.size());

        List<MatchingCandidateResponse> candidates = selectedUsers.stream()
                .map(this::toMatchingCandidateResponse)
                .toList();

        if (candidates.size() >= RECOMMENDATION_LIMIT) {
            user.consumeTickets(1);
            log.info("🎫 매칭 티켓 사용: userId={}, 사용한 티켓=1, 남은 티켓={}", userId, user.getTickets());
        } else {
            log.info("추천 후보가 1명 이하라 티켓을 차감하지 않습니다. userId={}, candidateCount={}, 남은 티켓={}",
                    userId, candidates.size(), user.getTickets());
        }

        analyticsService.trackServerEvent(
                AnalyticsEventType.MATCH_RECOMMENDATION_REFRESHED,
                userId,
                Map.of(
                        "candidateCount", candidates.size(),
                        "ticketsRemaining", user.getTickets()
                )
        );

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
            if (connection.getStatus() == ConnectionStatus.ACCEPTED && isConnectionChatActive(connection)) {
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
            
            // REJECTED 또는 종료된 ACCEPTED는 재요청 가능
            if (connection.getStatus() == ConnectionStatus.REJECTED || connection.getStatus() == ConnectionStatus.ACCEPTED) {
                log.info("🔄 거절된 요청 재시도: userId={}, targetUserId={}, connectionId={}", 
                        userId, targetUserId, connection.getId());
                connection.reopenAsPending(userId);
                
                // ✅ 모든 검증 완료 후 티켓 차감
                user.consumeTickets(2);
                log.info("🎫 컨택 티켓 사용: userId={}, 사용한 티켓=2, 남은 티켓={}", userId, user.getTickets());
                sendMatchRequestReceivedNotification(userId, targetUserId, connection);
                analyticsService.trackServerEvent(
                        AnalyticsEventType.MATCH_REQUEST_SENT,
                        userId,
                        Map.of(
                                "targetUserId", targetUserId,
                                "connectionId", connection.getId()
                        )
                );

                return new MatchingConnectResponse(null, targetUserId, false);
            }
        }

        MatchingConnection connection;
        try {
            // 새로운 요청 생성 (PENDING 상태로 저장)
            connection = matchingConnectionRepository.save(
                    MatchingConnection.createPending(userId, targetUserId)
            );
        } catch (DataIntegrityViolationException e) {
            log.warn("⚠️ 컨택 동시성 충돌 감지: requester={}, target={}", userId, targetUserId);
            MatchingConnection raced = matchingConnectionRepository.findByUser1IdAndUser2Id(user1, user2)
                    .orElseThrow(() -> e);
            return resolveConnectionStateAfterRace(userId, targetUserId, user, raced);
        }

        // ✅ 모든 검증과 로직이 성공한 후에만 티켓 차감
        user.consumeTickets(2);
        log.info("🎫 컨택 티켓 사용: userId={}, 사용한 티켓=2, 남은 티켓={}", userId, user.getTickets());

        log.info("✅ 매칭 요청 생성 완료: requester={}, target={}, connectionId={}", userId, targetUserId, connection.getId());
        sendMatchRequestReceivedNotification(userId, targetUserId, connection);

        analyticsService.trackServerEvent(
                AnalyticsEventType.MATCH_REQUEST_SENT,
                userId,
                Map.of(
                        "targetUserId", targetUserId,
                        "connectionId", connection.getId()
                )
        );

        return new MatchingConnectResponse(null, targetUserId, false);
    }

    private MatchingConnectResponse resolveConnectionStateAfterRace(Long userId,
                                                                    Long targetUserId,
                                                                    User requester,
                                                                    MatchingConnection connection) {
        if (connection.getStatus() == ConnectionStatus.PENDING) {
            throw new MatchingException(MatchingErrorCode.ALREADY_CONNECTED);
        }

        if (connection.getStatus() == ConnectionStatus.ACCEPTED && isConnectionChatActive(connection)) {
            return new MatchingConnectResponse(connection.getChatRoomId(), targetUserId, true);
        }

        connection.reopenAsPending(userId);
        requester.consumeTickets(2);
        log.info("🎫 컨택 티켓 사용(경쟁복구): userId={}, 사용한 티켓=2, 남은 티켓={}", userId, requester.getTickets());
        sendMatchRequestReceivedNotification(userId, targetUserId, connection);
        analyticsService.trackServerEvent(
                AnalyticsEventType.MATCH_REQUEST_SENT,
                userId,
                Map.of(
                        "targetUserId", targetUserId,
                        "connectionId", connection.getId()
                )
        );
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
        UserProfileResponse profileResponse = (profile != null) ? UserProfileResponse.from(profile, imageUrlBase) : null;

        boolean profileImageUploaded = userMilestoneRepository.existsByUserIdAndMilestoneType(
                otherUserId, MilestoneType.PROFILE_IMAGE_UPLOADED
        );
        boolean emailVerified = userMilestoneRepository.existsByUserIdAndMilestoneType(
                otherUserId, MilestoneType.EMAIL_VERIFIED
        );

        return MatchingRequestResponse.builder()
                .connectionId(conn.getId())
                .userId(otherUserId)
                .socialId(otherUser.getSocialId())
                .nickname(otherUser.getNickname())
                .deptName(otherUser.getDeptName())
                .studentNum(otherUser.getStudentNum())
                .age(profile != null ? profile.getAge() : null)
                .userStatus(otherUser.getStatus())
                .onboardingStatus(otherUser.getOnboardingStatus())
                .profileExists(profile != null)
                .profileImageUploaded(profileImageUploaded)
                .emailVerified(emailVerified)
                .tickets(otherUser.getTickets())
                .profile(profileResponse)
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

        if (!connection.isParticipant(userId)) {
            log.warn("⚠️ 요청 수락 권한 없음(비당사자): userId={}, connectionId={}", userId, connectionId);
            throw new MatchingException(MatchingErrorCode.INVALID_REQUEST);
        }

        if (!connection.isReceiver(userId)) {
            log.warn("⚠️ 요청 수락 권한 없음: userId={}, requesterId={}", userId, connection.getRequesterId());
            throw new MatchingException(MatchingErrorCode.INVALID_REQUEST);
        }

        if (connection.getStatus() != ConnectionStatus.PENDING) {
            log.warn("⚠️ 요청 상태 불일치: connectionId={}, status={}", connectionId, connection.getStatus());
            throw new MatchingException(MatchingErrorCode.INVALID_REQUEST);
        }

        Long otherUserId = connection.getOtherUserId(userId);

        // ACCEPT 시점마다 새로운 PERSONAL 방을 생성한다.
        ChatRoomResponse room = chatService.createOrGetPersonalRoomForConnection(
                connection.getId(),
                connection.getUser1Id(),
                connection.getUser2Id(),
                "소개팅 1:1"
        );
        log.debug("💌 채팅방 생성됨: chatRoomId={}", room.getId());

        // 연결 수락
        connection.accept(room.getId());
        sendMatchRequestAcceptedNotification(connection, userId, room.getId());

        log.info("✅ 매칭 요청 수락 완료: 수락자userId={}, 요청자userId={}, connectionId={}, chatRoomId={}", 
                userId, connection.getRequesterId(), connectionId, room.getId());

        analyticsService.trackServerEvent(
                AnalyticsEventType.MATCH_REQUEST_ACCEPTED,
                userId,
                Map.of(
                        "connectionId", connection.getId(),
                        "chatRoomId", room.getId(),
                        "targetUserId", otherUserId
                )
        );

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

        if (!connection.isParticipant(userId)) {
            log.warn("⚠️ 요청 거절 권한 없음(비당사자): userId={}, connectionId={}", userId, connectionId);
            throw new MatchingException(MatchingErrorCode.INVALID_REQUEST);
        }

        if (!connection.isReceiver(userId)) {
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
        sendMatchRequestRejectedNotification(connection, userId);

        log.info("✅ 매칭 요청 거절 완료: 거절자userId={}, 요청자userId={}, connectionId={}", 
                userId, connection.getRequesterId(), connectionId);

        return MatchingResponseResponse.builder()
                .connectionId(connection.getId())
                .targetUserId(otherUserId)
                .chatRoomId(null)
                .status(connection.getStatus())
                .build();
    }

    private MatchingCandidateResponse toMatchingCandidateResponse(User user) {
        UserProfile profile = user.getUserProfile();

        UserProfileResponse profileResponse = null;
        if (profile != null) {
            profileResponse = UserProfileResponse.from(profile, imageUrlBase);
        }

        boolean profileImageUploaded = userMilestoneRepository.existsByUserIdAndMilestoneType(
                user.getId(), MilestoneType.PROFILE_IMAGE_UPLOADED
        );
        boolean emailVerified = userMilestoneRepository.existsByUserIdAndMilestoneType(
                user.getId(), MilestoneType.EMAIL_VERIFIED
        );

        return MatchingCandidateResponse.builder()
                .userId(user.getId())
                .socialId(user.getSocialId())
                .nickname(user.getNickname())
                .deptName(user.getDeptName())
                .studentNum(user.getStudentNum())
                .age(profile != null ? profile.getAge() : null)
                .status(user.getStatus())
                .onboardingStatus(user.getOnboardingStatus())
                .profileExists(profile != null)
                .profileImageUploaded(profileImageUploaded)
                .emailVerified(emailVerified)
                .tickets(user.getTickets())
                .profile(profileResponse)
                .build();
    }

    private boolean isConnectionChatActive(MatchingConnection connection) {
        Long roomId = connection.getChatRoomId();
        if (roomId == null) {
            return false;
        }
        return chatRoomMemberRepository.existsByChatRoomIdAndUserId(roomId, connection.getUser1Id())
                && chatRoomMemberRepository.existsByChatRoomIdAndUserId(roomId, connection.getUser2Id());
    }

    private void saveExposureIfAbsent(Long userId, Long candidateUserId) {
        if (matchingExposureRepository.existsByUserIdAndCandidateUserId(userId, candidateUserId)) {
            return;
        }
        try {
            matchingExposureRepository.save(MatchingExposure.create(userId, candidateUserId));
        } catch (DataIntegrityViolationException e) {
            // 동시성으로 동일 노출이 이미 기록된 경우 무시
            log.debug("노출 이력 중복 무시: userId={}, candidateUserId={}", userId, candidateUserId);
        }
    }

    private void sendMatchRequestReceivedNotification(Long requesterUserId,
                                                      Long targetUserId,
                                                      MatchingConnection connection) {
        try {
            User requester = getRequiredUser(requesterUserId);
            String requesterNickname = requester.getNickname() != null && !requester.getNickname().isBlank()
                    ? requester.getNickname()
                    : "상대방";

            var payload = objectMapper.createObjectNode();
            payload.put("connectionId", connection.getId());
            payload.put("requesterUserId", requesterUserId);
            payload.put("requesterNickname", requesterNickname);

            notificationService.createAndEnqueue(new NotificationService.CreateCommand(
                    targetUserId,
                    NotificationType.MATCH_REQUEST_RECEIVED,
                    "새 매칭 요청",
                    requesterNickname + "님이 연결 요청을 보냈어요.",
                    "airconnect://matching/requests",
                    requesterUserId,
                    toFullImageUrl(extractProfileImagePath(requester)),
                    payload.toString(),
                    buildMatchDedupeKey(connection, "received", connection.getConnectedAt())
            ));
        } catch (Exception e) {
            log.error("매칭 요청 수신 알림 저장에 실패했습니다. requesterUserId={}, targetUserId={}, connectionId={}",
                    requesterUserId, targetUserId, connection.getId(), e);
        }
    }

    private void sendMatchRequestAcceptedNotification(MatchingConnection connection,
                                                      Long acceptedUserId,
                                                      Long chatRoomId) {
        try {
            User acceptedUser = getRequiredUser(acceptedUserId);
            String acceptedNickname = acceptedUser.getNickname() != null && !acceptedUser.getNickname().isBlank()
                    ? acceptedUser.getNickname()
                    : "상대방";

            var payload = objectMapper.createObjectNode();
            payload.put("connectionId", connection.getId());
            payload.put("chatRoomId", chatRoomId);
            payload.put("partnerUserId", acceptedUserId);
            payload.put("partnerNickname", acceptedNickname);

            notificationService.createAndEnqueue(new NotificationService.CreateCommand(
                    connection.getRequesterId(),
                    NotificationType.MATCH_REQUEST_ACCEPTED,
                    "매칭이 수락되었어요",
                    acceptedNickname + "님이 요청을 수락했어요. 지금 바로 대화를 시작해보세요.",
                    "airconnect://chat/rooms/" + chatRoomId,
                    acceptedUserId,
                    toFullImageUrl(extractProfileImagePath(acceptedUser)),
                    payload.toString(),
                    buildMatchDedupeKey(connection, "accepted", connection.getRespondedAt())
            ));
        } catch (Exception e) {
            log.error("매칭 수락 알림 저장에 실패했습니다. connectionId={}, requesterUserId={}, acceptedUserId={}",
                    connection.getId(), connection.getRequesterId(), acceptedUserId, e);
        }
    }

    private void sendMatchRequestRejectedNotification(MatchingConnection connection, Long rejectedUserId) {
        try {
            User rejectedUser = getRequiredUser(rejectedUserId);
            String rejectedNickname = rejectedUser.getNickname() != null && !rejectedUser.getNickname().isBlank()
                    ? rejectedUser.getNickname()
                    : "상대방";

            var payload = objectMapper.createObjectNode();
            payload.put("connectionId", connection.getId());
            payload.put("rejectedUserId", rejectedUserId);
            payload.put("rejectedNickname", rejectedNickname);

            notificationService.createAndEnqueue(new NotificationService.CreateCommand(
                    connection.getRequesterId(),
                    NotificationType.MATCH_REQUEST_REJECTED,
                    "매칭 요청이 거절되었어요",
                    rejectedNickname + "님이 이번 요청을 거절했어요.",
                    "airconnect://matching/requests",
                    rejectedUserId,
                    toFullImageUrl(extractProfileImagePath(rejectedUser)),
                    payload.toString(),
                    buildMatchDedupeKey(connection, "rejected", connection.getRespondedAt())
            ));
        } catch (Exception e) {
            log.error("매칭 거절 알림 저장에 실패했습니다. connectionId={}, requesterUserId={}, rejectedUserId={}",
                    connection.getId(), connection.getRequesterId(), rejectedUserId, e);
        }
    }

    private String buildMatchDedupeKey(MatchingConnection connection, String action, Object timestamp) {
        return "match-request:" + connection.getId() + ":" + action + ":" + String.valueOf(timestamp);
    }

    private User getRequiredUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new MatchingException(MatchingErrorCode.USER_NOT_FOUND));
    }

    private String extractProfileImagePath(User user) {
        if (user == null || user.getUserProfile() == null) {
            return null;
        }
        return user.getUserProfile().getProfileImagePath();
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

