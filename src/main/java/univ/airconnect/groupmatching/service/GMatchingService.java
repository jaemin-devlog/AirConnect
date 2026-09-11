package univ.airconnect.groupmatching.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import univ.airconnect.analytics.domain.AnalyticsEventType;
import univ.airconnect.analytics.service.AnalyticsService;
import univ.airconnect.auth.exception.AuthErrorCode;
import univ.airconnect.auth.exception.AuthException;
import univ.airconnect.chat.domain.entity.ChatRoom;
import univ.airconnect.chat.service.ChatService;
import univ.airconnect.global.error.BusinessException;
import univ.airconnect.global.error.ErrorCode;
import univ.airconnect.groupmatching.domain.GMatchResultStatus;
import univ.airconnect.groupmatching.domain.GTeamGender;
import univ.airconnect.groupmatching.domain.GTeamSize;
import univ.airconnect.groupmatching.domain.GTemporaryTeamRoomStatus;
import univ.airconnect.groupmatching.domain.entity.GFinalGroupChatRoom;
import univ.airconnect.groupmatching.domain.entity.GMatchResult;
import univ.airconnect.groupmatching.domain.entity.GTemporaryTeamMember;
import univ.airconnect.groupmatching.domain.entity.GTemporaryTeamRoom;
import univ.airconnect.groupmatching.repository.GFinalGroupChatRoomRepository;
import univ.airconnect.groupmatching.repository.GMatchResultRepository;
import univ.airconnect.groupmatching.repository.GTemporaryTeamMemberRepository;
import univ.airconnect.groupmatching.repository.GTemporaryTeamRoomRepository;
import univ.airconnect.matching.dto.response.MatchingCandidateResponse;
import univ.airconnect.iap.domain.entity.TicketLedger;
import univ.airconnect.iap.repository.TicketLedgerRepository;
import univ.airconnect.notification.domain.NotificationType;
import univ.airconnect.notification.service.NotificationService;
import univ.airconnect.user.domain.Gender;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.domain.entity.UserProfile;
import univ.airconnect.user.dto.response.UserProfileResponse;
import univ.airconnect.user.repository.UserProfileRepository;
import univ.airconnect.user.repository.UserRepository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.security.SecureRandom;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GMatchingService {

    private static final Set<GTemporaryTeamRoomStatus> ACTIVE_ROOM_STATUSES = EnumSet.of(
            GTemporaryTeamRoomStatus.OPEN,
            GTemporaryTeamRoomStatus.READY_CHECK,
            GTemporaryTeamRoomStatus.QUEUE_WAITING,
            GTemporaryTeamRoomStatus.MATCHED
    );

    private static final Set<GMatchResultStatus> ACTIVE_MATCH_STATUSES = EnumSet.of(
            GMatchResultStatus.MATCHED,
            GMatchResultStatus.FINAL_ROOM_CREATED
    );

    private static final int FULL_QUEUE_SCAN = -1;
    private static final SecureRandom INVITE_CODE_RANDOM = new SecureRandom();
    private static final Duration MATCH_PROCESS_LOCK_TTL = Duration.ofSeconds(5);
    private static final DefaultRedisScript<Long> RELEASE_PROCESS_LOCK_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            else
                return 0
            end
            """, Long.class);
    private static final Duration MATCH_FINALIZATION_DELAY = Duration.ofSeconds(1);
    private static final Duration QUEUE_TOKEN_TTL = Duration.ofHours(12);
    private static final int PROCESS_LOCK_RETRY_COUNT = 20;
    private static final long PROCESS_LOCK_RETRY_DELAY_MS = 50L;
    private static final String MATCH_COMPLETED_FINAL_CHAT_CREATED_MESSAGE =
            "\uB9E4\uCE6D\uC774 \uC644\uB8CC\uB418\uC5C8\uC2B5\uB2C8\uB2E4. \uCD5C\uC885 \uADF8\uB8F9 \uCC44\uD305\uBC29\uC774 \uC0DD\uC131\uB418\uC5C8\uC2B5\uB2C8\uB2E4.";

    private final GTemporaryTeamRoomRepository temporaryTeamRoomRepository;
    private final GTemporaryTeamMemberRepository temporaryTeamMemberRepository;
    private final GMatchResultRepository matchResultRepository;
    private final GFinalGroupChatRoomRepository finalGroupChatRoomRepository;
    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final ChatService chatService;
    private final GMatchingEventPublisher matchingEventPublisher;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final AnalyticsService analyticsService;
    private final TicketLedgerRepository ticketLedgerRepository;
    private final PlatformTransactionManager transactionManager;

    @Value("${app.upload.profile-image-url-base:http://localhost:8080/api/v1/users/profile-images}")
    private String imageUrlBase;

    /** 초대 코드 전용 팀을 생성한다. 채팅방은 매칭 성사 후에만 만든다. */
    @Transactional
    public GTemporaryTeamRoom createTemporaryTeamRoom(Long leaderUserId, GTeamSize teamSize) {
        userRepository.findByIdForUpdate(leaderUserId)
                .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND));
        ensureUserHasNoActiveTeamRoom(leaderUserId);
        GTeamGender teamGender = resolveUserTeamGender(leaderUserId);
        GTemporaryTeamRoom teamRoom = GTemporaryTeamRoom.createInviteOnly(leaderUserId, teamGender, teamSize);
        teamRoom.assignInviteCode(generateUniqueInviteCode());
        temporaryTeamRoomRepository.save(teamRoom);
        temporaryTeamMemberRepository.save(GTemporaryTeamMember.create(teamRoom.getId(), leaderUserId, true));
        analyticsService.trackServerEvent(AnalyticsEventType.TEAM_ROOM_CREATED, leaderUserId,
                Map.of("teamRoomId", teamRoom.getId(), "teamSize", teamSize.name(),
                        "teamGender", teamGender.name()));
        return teamRoom;
    }

    @Transactional
    public GTemporaryTeamRoom expelTeamMember(Long teamRoomId, Long requestUserId, Long targetUserId) {
        GTemporaryTeamRoom teamRoom = temporaryTeamRoomRepository.findByIdForUpdate(teamRoomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEAM_ROOM_NOT_FOUND));

        validateLeaderMembership(teamRoom, teamRoomId, requestUserId);
        if (Objects.equals(requestUserId, targetUserId)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "방장은 본인을 추방할 수 없습니다.");
        }

        GTemporaryTeamMember member = temporaryTeamMemberRepository.findByTeamRoomIdAndUserId(teamRoomId, targetUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEAM_MEMBER_NOT_FOUND));
        if (!member.isActiveMember()) {
            throw new BusinessException(ErrorCode.TEAM_MEMBER_NOT_FOUND);
        }
        if (member.isLeader()) {
            throw new BusinessException(ErrorCode.LEADER_ONLY_ACTION);
        }
        if (!teamRoom.getStatus().canModifyMembers()) {
            throw new BusinessException(ErrorCode.TEAM_ROOM_STATE_INVALID);
        }

        User targetUser = findUserOrThrow(targetUserId);
        member.markExpelled();
        teamRoom.removeMember();

        notifyTeamMemberLeft(teamRoom, targetUserId, targetUser.getNickname());
        matchingEventPublisher.publishStatus(teamRoomId, teamRoom.getStatus().name());
        analyticsService.trackServerEvent(
                AnalyticsEventType.TEAM_ROOM_LEFT,
                targetUserId,
                Map.of(
                        "teamRoomId", teamRoomId,
                        "memberCount", teamRoom.getCurrentMemberCount(),
                        "expelled", true
                )
        );

        return teamRoom;
    }

    /**
     * 초대 코드로 임시 팀방에 입장한다.
     */
    @Transactional
    public GTemporaryTeamRoom joinRoomByInviteCode(String inviteCode, Long userId) {
        if (inviteCode == null || inviteCode.isBlank()) {
            throw new BusinessException(ErrorCode.INVITE_CODE_REQUIRED);
        }

        GTemporaryTeamRoom teamRoom = temporaryTeamRoomRepository.findByInviteCode(inviteCode.trim().toUpperCase(java.util.Locale.ROOT))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INVITE_CODE));

        teamRoom = temporaryTeamRoomRepository.findByIdForUpdate(teamRoom.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.TEAM_ROOM_NOT_FOUND));

        if (!Objects.equals(teamRoom.getInviteCode(), inviteCode.trim().toUpperCase(java.util.Locale.ROOT))) {
            throw new BusinessException(ErrorCode.INVALID_INVITE_CODE);
        }
        return joinTeamRoomInternal(teamRoom, userId);
    }

    @Transactional
    public GTemporaryTeamRoom generateInviteCode(Long teamRoomId, Long requestUserId) {
        GTemporaryTeamRoom teamRoom = temporaryTeamRoomRepository.findByIdForUpdate(teamRoomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEAM_ROOM_NOT_FOUND));

        validateLeaderMembership(teamRoom, teamRoomId, requestUserId);
        teamRoom.assignInviteCode(generateUniqueInviteCode());
        matchingEventPublisher.publishStatus(teamRoomId, teamRoom.getDisplayStatus().name());
        return teamRoom;
    }

    @Transactional(readOnly = true)
    public MatchingCandidateResponse getTeamMemberProfile(
            Long teamRoomId,
            Long requestUserId,
            Long targetUserId
    ) {
        temporaryTeamRoomRepository.findById(teamRoomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEAM_ROOM_NOT_FOUND));
        validateActiveMembership(teamRoomId, requestUserId);

        if (Objects.equals(requestUserId, targetUserId)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "본인 프로필은 이 API로 조회할 수 없습니다.");
        }

        boolean activeTargetMember = temporaryTeamMemberRepository.existsByTeamRoomIdAndUserIdAndLeftAtIsNull(
                teamRoomId,
                targetUserId
        );
        if (!activeTargetMember) {
            throw new BusinessException(ErrorCode.TEAM_MEMBER_NOT_FOUND);
        }

        User targetUser = findUserWithProfileOrThrow(targetUserId);
        return toMatchingCandidateResponse(targetUser);
    }

    /**
     * Step 3. 방장이 매칭을 시작하고 Redis 큐에 등록한다.
     * - DB 상태를 QUEUE_WAITING 으로 바꾼다.
     * - Redis 리스트에 teamRoomId 를 넣는다.
     * - 등록 직후 가능한 매칭을 즉시 연속 처리한다.
     */
    @Transactional
    public QueueSnapshot startMatching(Long teamRoomId, Long requestUserId) {
        GTemporaryTeamRoom teamRoom = temporaryTeamRoomRepository.findByIdForUpdate(teamRoomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEAM_ROOM_NOT_FOUND));

        validateLeaderMembership(teamRoom, teamRoomId, requestUserId);
        if (teamRoom.getStatus() == GTemporaryTeamRoomStatus.QUEUE_WAITING) {
            return buildQueueSnapshot(teamRoom);
        }
        if (!teamRoom.getStatus().canEnterQueue()) {
            throw new BusinessException(ErrorCode.TEAM_ROOM_STATE_INVALID);
        }
        List<GTemporaryTeamMember> members = temporaryTeamMemberRepository
                .findByTeamRoomIdAndLeftAtIsNullOrderByJoinedAtAsc(teamRoomId);
        if (!teamRoom.isFull() || members.size() != teamRoom.getTeamSize().getValue()) {
            throw new BusinessException(ErrorCode.TEAM_ROOM_NOT_FULL);
        }
        // 시작 시 검증하고 실제 차감은 최종 채팅방 생성 트랜잭션에서 한다.
        for (GTemporaryTeamMember member : members) {
            validateUserTeamGender(member.getUserId(), teamRoom.getTeamGender());
            ensureUserHasEnoughGroupMatchTickets(findUserOrThrow(member.getUserId()),
                    requiredTicketsFor(teamRoom.getTeamSize()), "그룹매칭 시작 실패");
        }
        String queueToken = UUID.randomUUID().toString();

        teamRoom.startQueue(requestUserId, queueToken);

        QueueSnapshot snapshot = withQueueLockOrThrow(teamRoom.getTeamSize(), () -> {
            enqueueRoom(teamRoom.getTeamSize(), teamRoom.getId(), queueToken);
            processQueueUntilStableUnderLock(teamRoom.getTeamSize());
            return buildQueueSnapshot(teamRoom);
        });
        publishWaitingQueueSnapshots(teamRoom.getTeamSize());
        analyticsService.trackServerEvent(
                AnalyticsEventType.GROUP_QUEUE_STARTED,
                requestUserId,
                Map.of(
                        "teamRoomId", teamRoomId,
                        "teamSize", teamRoom.getTeamSize().name(),
                        "status", snapshot.status()
                )
        );
        return snapshot;
    }

    /**
     * 매칭 대기를 취소한다.
     */
    @Transactional
    public GTemporaryTeamRoom leaveMatchingQueue(Long teamRoomId, Long requestUserId) {
        GTemporaryTeamRoom teamRoom = temporaryTeamRoomRepository.findByIdForUpdate(teamRoomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEAM_ROOM_NOT_FOUND));

        validateActiveMembership(teamRoomId, requestUserId);

        if (teamRoom.getStatus().canModifyMembers()) {
            return teamRoom;
        }
        String queueToken = teamRoom.getQueueToken();
        teamRoom.leaveQueue();

        withQueueLockOrThrow(teamRoom.getTeamSize(), () -> {
            removeRoomFromRedisQueue(teamRoom.getTeamSize(), teamRoom.getId(), queueToken);
            return null;
        });
        matchingEventPublisher.publishStatus(teamRoomId, teamRoom.getStatus().name());
        publishWaitingQueueSnapshots(teamRoom.getTeamSize());
        notifyMatchingStopped(teamRoom, requestUserId, queueToken);
        return teamRoom;
    }

    @Transactional
    public QueueReconcileResult reconcileQueue(GTeamSize teamSize) {
        if (teamSize == null) {
            throw new BusinessException(ErrorCode.TEAM_SIZE_REQUIRED);
        }
        return withQueueLock(teamSize, () -> reconcileQueueUnderLock(teamSize))
                .orElseGet(() -> QueueReconcileResult.lockBusy(teamSize));
    }

    @Transactional
    public int processQueueUntilStable(GTeamSize teamSize) {
        if (teamSize == null) {
            throw new BusinessException(ErrorCode.TEAM_SIZE_REQUIRED);
        }
        int matchedCount = 0;

        while (true) {
            MatchSuccessResult matchResult = withQueueLock(
                    teamSize,
                    () -> processQueueUnderLock(teamSize, FULL_QUEUE_SCAN)
            ).orElse(null);
            if (matchResult == null) {
                if (matchedCount > 0) {
                    publishWaitingQueueSnapshots(teamSize);
                }
                return matchedCount;
            }
            matchedCount++;
        }
    }

    /** 현재 팀의 큐 상태를 조회한다. */

    @Transactional
    public QueueSnapshot getQueueSnapshot(Long teamRoomId, Long requestUserId) {
        GTemporaryTeamRoom teamRoom = getTeamRoomForMemberAction(teamRoomId, requestUserId);
        QueueSnapshot snapshot = buildQueueSnapshot(teamRoom);

        if (teamRoom.getStatus() == GTemporaryTeamRoomStatus.QUEUE_WAITING && snapshot.position() < 0) {
            QueueReconcileResult reconcileResult = reconcileQueue(teamRoom.getTeamSize());
            if (reconcileResult.lockAcquired()) {
                return buildQueueSnapshot(teamRoom);
            }
        }

        return snapshot;
    }

    private QueueSnapshot buildQueueSnapshot(GTemporaryTeamRoom teamRoom) {
        Long teamRoomId = teamRoom.getId();

        if (teamRoom.getStatus() == GTemporaryTeamRoomStatus.MATCHED || teamRoom.getStatus() == GTemporaryTeamRoomStatus.CLOSED) {
            Optional<GFinalGroupChatRoom> finalRoomOpt = findLatestFinalRoomByTeamRoomId(teamRoomId);
            if (finalRoomOpt.isPresent()) {
                GFinalGroupChatRoom finalRoom = finalRoomOpt.get();
                return QueueSnapshot.matched(teamRoomId, finalRoom.getId(), finalRoom.getChatRoomId());
            }
            return QueueSnapshot.statusOnly(teamRoomId, teamRoom.getStatus().name());
        }

        if (teamRoom.getStatus() != GTemporaryTeamRoomStatus.QUEUE_WAITING) {
            return QueueSnapshot.statusOnly(teamRoomId, teamRoom.getStatus().name());
        }

        // 같은 인원/성별의 팀들이 상대 팀을 기다리는 순번. Redis 잔여 항목은 포함하지 않는다.
        List<Long> queueRoomIds = temporaryTeamRoomRepository.findAllQueueWaitingRooms(teamRoom.getTeamSize())
                .stream()
                .filter(room -> room.getTeamGender() == teamRoom.getTeamGender())
                .map(GTemporaryTeamRoom::getId)
                .toList();
        int position = findQueuePosition(queueRoomIds, teamRoomId);
        int totalWaitingTeams = queueRoomIds.size();

        if (position < 0) {
            return new QueueSnapshot(teamRoomId, teamRoom.getStatus().name(), -1, -1, totalWaitingTeams, null, null);
        }

        return new QueueSnapshot(teamRoomId, teamRoom.getStatus().name(), position + 1, position, totalWaitingTeams, null, null);
    }

    /**
     * 매칭 큐를 한 번 처리한다.
     * - Redis 대기열 순서대로 후보 팀을 읽는다.
     * - 상태가 달라진 오래된 엔트리는 Redis 에서 정리한다.
     */
    @Transactional
    public MatchSuccessResult processQueue(GTeamSize teamSize, int scanSize) {
        if (teamSize == null) {
            throw new BusinessException(ErrorCode.TEAM_SIZE_REQUIRED);
        }
        MatchSuccessResult result = withQueueLock(
                teamSize,
                () -> processQueueUnderLock(teamSize, scanSize)
        ).orElse(null);
        if (result != null) {
            publishWaitingQueueSnapshots(teamSize);
        }
        return result;
    }

    private int processQueueUntilStableUnderLock(GTeamSize teamSize) {
        int matchedCount = 0;

        while (true) {
            MatchSuccessResult matchResult = processQueueUnderLock(teamSize, FULL_QUEUE_SCAN);
            if (matchResult == null) {
                return matchedCount;
            }
            matchedCount++;
        }
    }

    private MatchSuccessResult processQueueUnderLock(GTeamSize teamSize, int scanSize) {
        if (teamSize == null) {
            throw new BusinessException(ErrorCode.TEAM_SIZE_REQUIRED);
        }

        List<Long> orderedRoomIds = readQueueRoomIds(teamSize, scanSize);
        List<GTemporaryTeamRoom> queueCandidates = new ArrayList<>();
        Set<Long> seen = new LinkedHashSet<>();

        for (Long roomId : orderedRoomIds) {
            if (roomId == null) {
                continue;
            }

            if (!seen.add(roomId)) {
                removeRoomFromRedisQueue(teamSize, roomId, null);
                continue;
            }

            Optional<GTemporaryTeamRoom> roomOpt = temporaryTeamRoomRepository.findByIdForUpdate(roomId);
            if (roomOpt.isEmpty()) {
                removeRoomFromRedisQueue(teamSize, roomId, null);
                continue;
            }

            GTemporaryTeamRoom room = roomOpt.get();
            if (room.getStatus() != GTemporaryTeamRoomStatus.QUEUE_WAITING) {
                removeRoomFromRedisQueue(teamSize, roomId, room.getQueueToken());
                continue;
            }

            queueCandidates.add(room);
        }

        if (queueCandidates.size() < 2) {
            return null;
        }

        for (int i = 0; i < queueCandidates.size(); i++) {
            GTemporaryTeamRoom first = queueCandidates.get(i);
            for (int j = i + 1; j < queueCandidates.size(); j++) {
                GTemporaryTeamRoom second = queueCandidates.get(j);
                if (!first.canMatchWith(second)) {
                    continue;
                }
                return completeMatch(first, second);
            }
        }

        return null;
    }

    /**
     * 팀원이 임시 팀방에서 나간다.
     * - 방장은 leave 대신 cancel 을 사용한다.
     */
    @Transactional
    public GTemporaryTeamRoom leaveTeamRoom(Long teamRoomId, Long userId) {
        GTemporaryTeamRoom teamRoom = temporaryTeamRoomRepository.findByIdForUpdate(teamRoomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEAM_ROOM_NOT_FOUND));

        GTemporaryTeamMember member = temporaryTeamMemberRepository.findByTeamRoomIdAndUserId(teamRoomId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEAM_MEMBER_NOT_FOUND));

        if (!member.isActiveMember()) {
            // idempotent leave: already-left member should not fail retry calls.
            return teamRoom;
        }
        if (member.isLeader()) {
            throw new BusinessException(ErrorCode.LEADER_CANNOT_LEAVE);
        }

        User user = findUserOrThrow(userId);
        if (!teamRoom.getStatus().canModifyMembers()) {
            throw new BusinessException(ErrorCode.TEAM_ROOM_STATE_INVALID,
                    "매칭 대기를 먼저 중지한 후 팀에서 나갈 수 있습니다. 매칭 완료 후에는 나갈 수 없습니다.");
        }
        member.markLeft();
        teamRoom.removeMember();

        notifyTeamMemberLeft(teamRoom, userId, user.getNickname());
        matchingEventPublisher.publishStatus(teamRoomId, teamRoom.getStatus().name());
        analyticsService.trackServerEvent(
                AnalyticsEventType.TEAM_ROOM_LEFT,
                userId,
                Map.of(
                        "teamRoomId", teamRoomId,
                        "memberCount", teamRoom.getCurrentMemberCount()
                )
        );

        return teamRoom;
    }

    /**
     * 방장이 팀방 자체를 해산한다.
     */
    @Transactional
    public GTemporaryTeamRoom cancelTeamRoom(Long teamRoomId, Long requestUserId) {
        GTemporaryTeamRoom teamRoom = temporaryTeamRoomRepository.findByIdForUpdate(teamRoomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEAM_ROOM_NOT_FOUND));

        validateActiveMembership(teamRoomId, requestUserId);

        User leader = findUserOrThrow(requestUserId);
        String queueToken = teamRoom.getQueueToken();
        boolean wasQueueWaiting = teamRoom.getStatus() == GTemporaryTeamRoomStatus.QUEUE_WAITING;
        teamRoom.cancel(requestUserId);
        matchingEventPublisher.publishStatus(teamRoomId, teamRoom.getStatus().name());

        List<GTemporaryTeamMember> activeMembers = temporaryTeamMemberRepository
                .findByTeamRoomIdAndLeftAtIsNullOrderByJoinedAtAsc(teamRoomId);
        notifyTeamRoomCancelled(teamRoom, activeMembers, requestUserId, leader.getNickname());
        markMembersLeft(activeMembers);
        if (wasQueueWaiting) {
            withQueueLockOrThrow(teamRoom.getTeamSize(), () -> {
                removeRoomFromRedisQueue(teamRoom.getTeamSize(), teamRoomId, queueToken);
                return null;
            });
            publishWaitingQueueSnapshots(teamRoom.getTeamSize());
        }

        return teamRoom;
    }

    /** 내가 현재 참여 중인 활성 임시 팀방을 조회한다. */

    @Transactional(readOnly = true)
    public Optional<GTemporaryTeamRoom> findMyActiveTeamRoom(Long userId) {
        List<GTemporaryTeamRoom> rooms = temporaryTeamRoomRepository.findActiveRoomsByUserId(userId, ACTIVE_ROOM_STATUSES);
        return rooms.stream().findFirst();
    }

    @Transactional(readOnly = true)
    public Optional<GFinalGroupChatRoom> findMyActiveFinalRoom(Long userId) {
        List<GFinalGroupChatRoom> finalRooms = finalGroupChatRoomRepository.findActiveRoomsByUserId(userId);
        return finalRooms.stream().findFirst();
    }

    /** 특정 임시 팀방에 연결된 활성 최종 그룹 채팅방을 조회한다. */

    @Transactional(readOnly = true)
    public Optional<GFinalGroupChatRoom> findActiveFinalRoom(Long teamRoomId, Long requestUserId) {
        validateActiveMembershipOrClosedRoomAccess(teamRoomId, requestUserId);
        return findLatestFinalRoomByTeamRoomId(teamRoomId);
    }

    private GTemporaryTeamRoom joinTeamRoomInternal(GTemporaryTeamRoom teamRoom, Long userId) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND));
        if (temporaryTeamMemberRepository.existsByTeamRoomIdAndUserIdAndLeftAtIsNull(teamRoom.getId(), userId)) {
            return teamRoom;
        }
        ensureUserHasNoActiveTeamRoom(userId);

        if (Objects.equals(teamRoom.getLeaderId(), userId)) {
            throw new BusinessException(ErrorCode.LEADER_CANNOT_REJOIN);
        }
        if (teamRoom.getStatus().isTerminal()) {
            throw new BusinessException(ErrorCode.TEAM_ROOM_TERMINATED);
        }
        if (!teamRoom.getStatus().canModifyMembers()) {
            throw new BusinessException(ErrorCode.TEAM_ROOM_JOIN_NOT_ALLOWED);
        }
        if (teamRoom.isFull()) {
            throw new BusinessException(ErrorCode.TEAM_ROOM_FULL);
        }
        validateUserTeamGender(userId, teamRoom.getTeamGender());

        Optional<GTemporaryTeamMember> existingMember = temporaryTeamMemberRepository.findByTeamRoomIdAndUserId(teamRoom.getId(), userId);
        if (existingMember.isPresent()) {
            GTemporaryTeamMember member = existingMember.get();
            if (member.isActiveMember()) {
                throw new BusinessException(ErrorCode.ALREADY_TEAM_MEMBER);
            }
            if (member.wasExpelled()) {
                throw new BusinessException(ErrorCode.TEAM_ROOM_JOIN_NOT_ALLOWED, "추방된 임시방에는 다시 입장할 수 없습니다.");
            }
        }

        teamRoom.addMember();
        if (existingMember.isPresent()) {
            existingMember.get().rejoin();
        } else {
            temporaryTeamMemberRepository.save(GTemporaryTeamMember.create(teamRoom.getId(), userId, false));
        }

        notifyTeamMemberJoined(teamRoom, userId, user.getNickname());
        matchingEventPublisher.publishStatus(teamRoom.getId(), teamRoom.getStatus().name());
        analyticsService.trackServerEvent(
                AnalyticsEventType.TEAM_ROOM_JOINED,
                userId,
                Map.of(
                        "teamRoomId", teamRoom.getId(),
                        "memberCount", teamRoom.getCurrentMemberCount(),
                        "status", teamRoom.getStatus().name()
                )
        );

        return teamRoom;
    }

    private MatchSuccessResult completeMatch(GTemporaryTeamRoom first, GTemporaryTeamRoom second) {
        if (matchResultRepository.existsByTeamPairAndStatuses(first.getId(), second.getId(), ACTIVE_MATCH_STATUSES)
                || finalGroupChatRoomRepository.findByTeamPair(first.getId(), second.getId()).isPresent()) {
            removeRoomFromRedisQueue(first.getTeamSize(), first.getId(), first.getQueueToken());
            removeRoomFromRedisQueue(second.getTeamSize(), second.getId(), second.getQueueToken());
            return null;
        }

        List<GTemporaryTeamMember> firstMembers = temporaryTeamMemberRepository.findByTeamRoomIdAndLeftAtIsNullOrderByJoinedAtAsc(first.getId());
        List<GTemporaryTeamMember> secondMembers = temporaryTeamMemberRepository.findByTeamRoomIdAndLeftAtIsNullOrderByJoinedAtAsc(second.getId());

        validateReadyToFinalize(first, firstMembers);
        validateReadyToFinalize(second, secondMembers);

        String firstQueueToken = first.getQueueToken();
        String secondQueueToken = second.getQueueToken();

        first.markMatched();
        second.markMatched();

        GMatchResult matchResult = matchResultRepository.save(GMatchResult.create(first.getId(), second.getId()));

        if (shouldDelayMatchFinalization()) {
            removeRoomFromRedisQueue(first.getTeamSize(), first.getId(), firstQueueToken);
            removeRoomFromRedisQueue(second.getTeamSize(), second.getId(), secondQueueToken);
            return new MatchSuccessResult(
                    matchResult.getId(),
                    null,
                    null,
                    first.getId(),
                    second.getId()
            );
        }

        LinkedHashSet<Long> finalMemberIds = new LinkedHashSet<>();
        finalMemberIds.addAll(extractUserIds(firstMembers));
        finalMemberIds.addAll(extractUserIds(secondMembers));

        List<User> ticketUsers = lockAndValidateGroupMatchTickets(first.getTeamSize(), finalMemberIds);
        ChatRoom finalChatRoom = chatService.createGroupRoomWithMembers(
                buildFinalRoomName(first.getTeamSize()),
                finalMemberIds
        );

        GFinalGroupChatRoom finalGroupChatRoom = finalGroupChatRoomRepository.save(
                GFinalGroupChatRoom.create(
                        finalChatRoom.getId(),
                        first.getId(),
                        second.getId(),
                        matchResult.getId(),
                        first.getTeamSize()
                )
        );

        consumeGroupMatchTicketsAfterFinalRoomCreated(first.getTeamSize(), ticketUsers, matchResult.getId());
        matchResult.completeFinalRoomCreation(finalGroupChatRoom.getId());
        QueueSnapshot firstMatchedSnapshot = QueueSnapshot.matched(first.getId(), finalGroupChatRoom.getId(), finalChatRoom.getId());
        QueueSnapshot secondMatchedSnapshot = QueueSnapshot.matched(second.getId(), finalGroupChatRoom.getId(), finalChatRoom.getId());
        matchingEventPublisher.publishMatched(firstMatchedSnapshot);
        matchingEventPublisher.publishMatched(secondMatchedSnapshot);
        notifyGroupMatched(finalMemberIds, first.getId(), second.getId(), finalGroupChatRoom.getId(), finalChatRoom.getId());

        publishFinalGroupChatSystemMessages(first, second, finalChatRoom.getId());

        markMembersLeft(firstMembers);
        markMembersLeft(secondMembers);

        first.closeAfterFinalRoomCreated();
        second.closeAfterFinalRoomCreated();

        removeRoomFromRedisQueue(first.getTeamSize(), first.getId(), firstQueueToken);
        removeRoomFromRedisQueue(second.getTeamSize(), second.getId(), secondQueueToken);
        analyticsService.trackServerEvent(
                AnalyticsEventType.GROUP_MATCH_COMPLETED,
                first.getLeaderId(),
                Map.of(
                        "firstTeamRoomId", first.getId(),
                        "secondTeamRoomId", second.getId(),
                        "finalGroupRoomId", finalGroupChatRoom.getId(),
                        "finalChatRoomId", finalChatRoom.getId()
                )
        );

        return new MatchSuccessResult(
                matchResult.getId(),
                finalGroupChatRoom.getId(),
                finalChatRoom.getId(),
                first.getId(),
                second.getId()
        );
    }

    private void notifyTeamMemberJoined(GTemporaryTeamRoom teamRoom, Long joinedUserId, String joinedNickname) {
        List<Long> recipientIds = temporaryTeamMemberRepository.findByTeamRoomIdAndLeftAtIsNullOrderByJoinedAtAsc(teamRoom.getId())
                .stream()
                .map(GTemporaryTeamMember::getUserId)
                .filter(id -> !Objects.equals(id, joinedUserId))
                .distinct()
                .toList();
        if (recipientIds.isEmpty()) {
            return;
        }

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("teamRoomId", teamRoom.getId());
        payload.put("joinedUserId", joinedUserId);
        payload.put("joinedNickname", joinedNickname);
        payload.put("currentMemberCount", teamRoom.getCurrentMemberCount());
        payload.put("full", teamRoom.isFull());

        for (Long recipientId : recipientIds) {
            sendGroupNotification(
                    recipientId,
                    NotificationType.TEAM_MEMBER_JOINED,
                    "팀원 합류",
                    joinedNickname + "님이 팀에 합류했어요." + (teamRoom.isFull() ? " 모두 모였으니 방장이 매칭을 시작할 수 있어요." : ""),
                    teamRoomDeeplink(teamRoom.getId()),
                    joinedUserId,
                    payload.toString(),
                    null,
                    true
            );
        }
    }

    private void notifyMatchingStopped(GTemporaryTeamRoom teamRoom, Long actorId, String queueToken) {
        User actor = findUserOrThrow(actorId);
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("teamRoomId", teamRoom.getId());
        payload.put("stoppedByUserId", actorId);
        payload.put("status", teamRoom.getStatus().name());
        for (GTemporaryTeamMember member : temporaryTeamMemberRepository
                .findByTeamRoomIdAndLeftAtIsNullOrderByJoinedAtAsc(teamRoom.getId())) {
            if (Objects.equals(member.getUserId(), actorId)) continue;
            sendGroupNotification(member.getUserId(), NotificationType.TEAM_MATCHING_STOPPED,
                    "매칭 대기 중지", actor.getNickname() + "님이 매칭 대기를 중지했어요. 팀은 유지됩니다.",
                    teamRoomDeeplink(teamRoom.getId()), actorId, payload.toString(),
                    buildGroupMatchingDedupeKey("queue-stopped", teamRoom.getId(), queueToken), true);
        }
    }

    private void notifyTeamMemberLeft(GTemporaryTeamRoom teamRoom, Long leftUserId, String leftNickname) {
        List<Long> recipientIds = temporaryTeamMemberRepository.findByTeamRoomIdAndLeftAtIsNullOrderByJoinedAtAsc(teamRoom.getId())
                .stream()
                .map(GTemporaryTeamMember::getUserId)
                .filter(id -> !Objects.equals(id, leftUserId))
                .distinct()
                .toList();
        if (recipientIds.isEmpty()) {
            return;
        }

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("teamRoomId", teamRoom.getId());
        payload.put("leftUserId", leftUserId);
        payload.put("leftNickname", leftNickname);
        payload.put("currentMemberCount", teamRoom.getCurrentMemberCount());

        for (Long recipientId : recipientIds) {
            sendGroupNotification(
                    recipientId,
                    NotificationType.TEAM_MEMBER_LEFT,
                    "팀원 이탈",
                    leftNickname + "님이 팀에서 나갔어요.",
                    teamRoomDeeplink(teamRoom.getId()),
                    leftUserId,
                    payload.toString(),
                    null,
                    true
            );
        }
    }

    private void notifyTeamRoomCancelled(
            GTemporaryTeamRoom teamRoom,
            List<GTemporaryTeamMember> activeMembers,
            Long cancelledByUserId,
            String cancelledByNickname
    ) {
        if (activeMembers == null || activeMembers.isEmpty()) {
            return;
        }

        List<Long> recipientIds = activeMembers.stream()
                .map(GTemporaryTeamMember::getUserId)
                .filter(id -> !Objects.equals(id, cancelledByUserId))
                .distinct()
                .toList();
        if (recipientIds.isEmpty()) {
            return;
        }

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("teamRoomId", teamRoom.getId());
        payload.put("cancelledByUserId", cancelledByUserId);

        String dedupeKey = buildGroupMatchingDedupeKey(
                "team-room-cancelled",
                teamRoom.getId(),
                teamRoom.getCancelledAt() != null ? teamRoom.getCancelledAt() : LocalDateTime.now()
        );

        for (Long recipientId : recipientIds) {
            sendGroupNotification(
                    recipientId,
                    NotificationType.TEAM_ROOM_CANCELLED,
                    "팀 방이 해산됐어요",
                    cancelledByNickname + "님이 팀 방을 해산했어요.",
                    "airconnect://matching/team-rooms",
                    cancelledByUserId,
                    payload.toString(),
                    dedupeKey,
                    true
            );
        }
    }

    private void notifyGroupMatched(
            Collection<Long> userIds,
            Long firstTeamRoomId,
            Long secondTeamRoomId,
            Long finalGroupRoomId,
            Long finalChatRoomId
    ) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }

        String dedupeKey = "group-matched:" + finalGroupRoomId;
        for (Long recipientId : new LinkedHashSet<>(userIds)) {
            if (recipientId == null) {
                continue;
            }

            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("team1RoomId", firstTeamRoomId);
            payload.put("team2RoomId", secondTeamRoomId);
            payload.put("finalGroupRoomId", finalGroupRoomId);
            payload.put("finalChatRoomId", finalChatRoomId);
            payload.put("memberCount", userIds.size());

            sendGroupNotification(
                    recipientId,
                    NotificationType.GROUP_MATCHED,
                    "그룹 매칭 성사",
                    "상대 팀과 매칭됐어요. 최종 그룹 채팅방으로 이동해보세요.",
                    "airconnect://group-chat/final/" + finalGroupRoomId,
                    null,
                    payload.toString(),
                    dedupeKey,
                    true
            );
        }
    }

    private void sendGroupNotification(
            Long recipientUserId,
            NotificationType type,
            String title,
            String body,
            String deeplink,
            Long actorUserId,
            String payloadJson,
            String dedupeKey,
            boolean enqueuePush
    ) {
        if (recipientUserId == null) {
            return;
        }
        try {
            NotificationService.CreateCommand command = new NotificationService.CreateCommand(
                    recipientUserId,
                    type,
                    title,
                    body,
                    deeplink,
                    actorUserId,
                    null,
                    payloadJson,
                    dedupeKey
            );

            if (enqueuePush) {
                notificationService.createAndEnqueue(command);
                return;
            }
            notificationService.create(command);
        } catch (Exception e) {
            log.error("과팅 알림 발송에 실패했습니다. type={}, recipientUserId={}", type, recipientUserId, e);
        }
    }

    private String buildGroupMatchingDedupeKey(String action, Long teamRoomId, Object discriminator) {
        return "group-matching:" + action + ":" + teamRoomId + ":" + String.valueOf(discriminator);
    }

    private String teamRoomDeeplink(Long teamRoomId) {
        return "airconnect://matching/team-rooms/" + teamRoomId;
    }

    private void validateReadyToFinalize(GTemporaryTeamRoom room, List<GTemporaryTeamMember> members) {
        if (room.getStatus() != GTemporaryTeamRoomStatus.QUEUE_WAITING) {
            throw new BusinessException(ErrorCode.QUEUE_WAITING_REQUIRED);
        }
        if (members.size() != room.getTeamSize().getValue()) {
            throw new BusinessException(ErrorCode.TEAM_MEMBER_COUNT_MISMATCH);
        }
    }

    private void validateActiveMembership(Long teamRoomId, Long userId) {
        boolean isActiveMember = temporaryTeamMemberRepository.existsByTeamRoomIdAndUserIdAndLeftAtIsNull(teamRoomId, userId);
        if (!isActiveMember) {
            throw new BusinessException(ErrorCode.TEAM_MEMBER_FORBIDDEN);
        }
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public int finalizePendingMatches() {
        LocalDateTime threshold = LocalDateTime.now().minus(MATCH_FINALIZATION_DELAY);
        List<Long> matchedResultIds = matchResultRepository.findPendingFinalizationIds(threshold);
        TransactionTemplate finalization = new TransactionTemplate(transactionManager);
        finalization.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        int finalizedCount = 0;

        for (Long matchResultId : matchedResultIds) {
            try {
                // Catch outside this boundary: a failed result rolls back its room,
                // memberships, balances and history without affecting other results.
                if (Boolean.TRUE.equals(finalization.execute(status -> finalizeMatchedResult(matchResultId)))) {
                    finalizedCount++;
                }
            } catch (RuntimeException e) {
                log.error("Failed to finalize delayed group match. matchResultId={}", matchResultId, e);
            }
        }

        return finalizedCount;
    }

    private boolean finalizeMatchedResult(Long matchResultId) {
        GMatchResult matchResult = matchResultRepository.findByIdForUpdate(matchResultId)
                .orElse(null);
        if (matchResult == null || matchResult.getStatus() != GMatchResultStatus.MATCHED) {
            return false;
        }

        LocalDateTime threshold = LocalDateTime.now().minus(MATCH_FINALIZATION_DELAY);
        if (matchResult.getMatchedAt() == null || matchResult.getMatchedAt().isAfter(threshold)) {
            return false;
        }

        GTemporaryTeamRoom first = temporaryTeamRoomRepository.findByIdForUpdate(matchResult.getTeam1RoomId())
                .orElseThrow(() -> new BusinessException(ErrorCode.TEAM_ROOM_NOT_FOUND));
        GTemporaryTeamRoom second = temporaryTeamRoomRepository.findByIdForUpdate(matchResult.getTeam2RoomId())
                .orElseThrow(() -> new BusinessException(ErrorCode.TEAM_ROOM_NOT_FOUND));

        if (first.getStatus() != GTemporaryTeamRoomStatus.MATCHED || second.getStatus() != GTemporaryTeamRoomStatus.MATCHED) {
            log.warn(
                    "Skipping delayed group match finalization because room status changed. matchResultId={}, firstStatus={}, secondStatus={}",
                    matchResultId,
                    first.getStatus(),
                    second.getStatus()
            );
            return false;
        }

        List<GTemporaryTeamMember> firstMembers = temporaryTeamMemberRepository.findByTeamRoomIdAndLeftAtIsNullOrderByJoinedAtAsc(first.getId());
        List<GTemporaryTeamMember> secondMembers = temporaryTeamMemberRepository.findByTeamRoomIdAndLeftAtIsNullOrderByJoinedAtAsc(second.getId());
        validateMatchedMembersForFinalization(first, firstMembers);
        validateMatchedMembersForFinalization(second, secondMembers);

        LinkedHashSet<Long> finalMemberIds = new LinkedHashSet<>();
        finalMemberIds.addAll(extractUserIds(firstMembers));
        finalMemberIds.addAll(extractUserIds(secondMembers));

        List<User> ticketUsers = lockAndValidateGroupMatchTickets(first.getTeamSize(), finalMemberIds);
        ChatRoom finalChatRoom = chatService.createGroupRoomWithMembers(
                buildFinalRoomName(first.getTeamSize()),
                finalMemberIds
        );

        GFinalGroupChatRoom finalGroupChatRoom = finalGroupChatRoomRepository.save(
                GFinalGroupChatRoom.create(
                        finalChatRoom.getId(),
                        first.getId(),
                        second.getId(),
                        matchResult.getId(),
                        first.getTeamSize()
                )
        );

        consumeGroupMatchTicketsAfterFinalRoomCreated(first.getTeamSize(), ticketUsers, matchResult.getId());
        matchResult.completeFinalRoomCreation(finalGroupChatRoom.getId());

        QueueSnapshot firstMatchedSnapshot = QueueSnapshot.matched(first.getId(), finalGroupChatRoom.getId(), finalChatRoom.getId());
        QueueSnapshot secondMatchedSnapshot = QueueSnapshot.matched(second.getId(), finalGroupChatRoom.getId(), finalChatRoom.getId());
        matchingEventPublisher.publishMatched(firstMatchedSnapshot);
        matchingEventPublisher.publishMatched(secondMatchedSnapshot);
        notifyGroupMatched(finalMemberIds, first.getId(), second.getId(), finalGroupChatRoom.getId(), finalChatRoom.getId());

        publishFinalGroupChatSystemMessages(first, second, finalChatRoom.getId());

        markMembersLeft(firstMembers);
        markMembersLeft(secondMembers);

        first.closeAfterFinalRoomCreated();
        second.closeAfterFinalRoomCreated();

        removeRoomFromRedisQueue(first.getTeamSize(), first.getId(), first.getQueueToken());
        removeRoomFromRedisQueue(second.getTeamSize(), second.getId(), second.getQueueToken());
        analyticsService.trackServerEvent(
                AnalyticsEventType.GROUP_MATCH_COMPLETED,
                first.getLeaderId(),
                Map.of(
                        "firstTeamRoomId", first.getId(),
                        "secondTeamRoomId", second.getId(),
                        "finalGroupRoomId", finalGroupChatRoom.getId(),
                        "finalChatRoomId", finalChatRoom.getId()
                )
        );
        return true;
    }

    private boolean shouldDelayMatchFinalization() {
        return MATCH_FINALIZATION_DELAY != null && !MATCH_FINALIZATION_DELAY.isNegative();
    }

    private void publishFinalGroupChatSystemMessages(
            GTemporaryTeamRoom first,
            GTemporaryTeamRoom second,
            Long finalChatRoomId
    ) {
        chatService.publishEnterMessage(
                finalChatRoomId,
                first.getLeaderId(),
                MATCH_COMPLETED_FINAL_CHAT_CREATED_MESSAGE
        );
    }

    private void validateMatchedMembersForFinalization(GTemporaryTeamRoom room, List<GTemporaryTeamMember> members) {
        if (room.getStatus() != GTemporaryTeamRoomStatus.MATCHED) {
            throw new BusinessException(ErrorCode.MATCH_RESULT_STATE_INVALID);
        }
        if (members.size() != room.getTeamSize().getValue()) {
            throw new BusinessException(ErrorCode.TEAM_MEMBER_COUNT_MISMATCH);
        }
    }

    private void validateLeaderMembership(GTemporaryTeamRoom teamRoom, Long teamRoomId, Long userId) {
        validateActiveMembership(teamRoomId, userId);
        if (!teamRoom.isLeader(userId)) {
            throw new BusinessException(ErrorCode.LEADER_ONLY_ACTION);
        }
    }

    @Transactional(readOnly = true)
    public boolean canSubscribeTeamRoom(Long teamRoomId, Long userId) {
        boolean activeMember = temporaryTeamMemberRepository.existsByTeamRoomIdAndUserIdAndLeftAtIsNull(teamRoomId, userId);
        if (activeMember) {
            return true;
        }

        Optional<GTemporaryTeamMember> memberOpt = temporaryTeamMemberRepository.findByTeamRoomIdAndUserId(teamRoomId, userId);
        if (memberOpt.isEmpty()) {
            return false;
        }
        if (memberOpt.get().wasExpelled()) {
            return false;
        }

        Optional<GTemporaryTeamRoom> roomOpt = temporaryTeamRoomRepository.findById(teamRoomId);
        if (roomOpt.isEmpty()) {
            return false;
        }

        GTemporaryTeamRoomStatus status = roomOpt.get().getStatus();
        return status == GTemporaryTeamRoomStatus.CLOSED && isFinalRoomParticipant(teamRoomId, userId);
    }

    private void validateActiveMembershipOrClosedRoomAccess(Long teamRoomId, Long userId) {
        boolean currentActiveMember = temporaryTeamMemberRepository.existsByTeamRoomIdAndUserIdAndLeftAtIsNull(teamRoomId, userId);
        if (currentActiveMember) {
            return;
        }

        GTemporaryTeamMember member = temporaryTeamMemberRepository.findByTeamRoomIdAndUserId(teamRoomId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEAM_ROOM_ACCESS_FORBIDDEN));

        if (member.getLeftAt() == null) {
            return;
        }

        GTemporaryTeamRoom room = temporaryTeamRoomRepository.findById(teamRoomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEAM_ROOM_NOT_FOUND));

        if (member.wasExpelled() || room.getStatus() != GTemporaryTeamRoomStatus.CLOSED
                || !isFinalRoomParticipant(teamRoomId, userId)) {
            throw new BusinessException(ErrorCode.TEAM_ROOM_ACCESS_FORBIDDEN);
        }
    }

    private boolean isFinalRoomParticipant(Long teamRoomId, Long userId) {
        return finalGroupChatRoomRepository.findActiveRoomsByUserId(userId).stream()
                .anyMatch(room -> Objects.equals(room.getTeam1RoomId(), teamRoomId)
                        || Objects.equals(room.getTeam2RoomId(), teamRoomId));
    }

    private GTemporaryTeamRoom getTeamRoomForMemberAction(Long teamRoomId, Long userId) {
        GTemporaryTeamRoom teamRoom = temporaryTeamRoomRepository.findByIdForUpdate(teamRoomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEAM_ROOM_NOT_FOUND));
        validateActiveMembership(teamRoomId, userId);
        return teamRoom;
    }

    private void ensureUserHasNoActiveTeamRoom(Long userId) {
        List<GTemporaryTeamRoom> activeRooms = temporaryTeamRoomRepository.findActiveRoomsByUserIdForUpdate(userId, ACTIVE_ROOM_STATUSES);
        if (!activeRooms.isEmpty()) {
            throw new BusinessException(ErrorCode.ACTIVE_TEAM_ROOM_EXISTS);
        }
    }

    private List<Long> extractUserIds(Collection<GTemporaryTeamMember> members) {
        return members.stream()
                .map(GTemporaryTeamMember::getUserId)
                .collect(Collectors.toList());
    }

    private void markMembersLeft(Collection<GTemporaryTeamMember> members) {
        for (GTemporaryTeamMember member : members) {
            if (member.isActiveMember()) {
                member.markLeft();
            }
        }
    }

    private Optional<GFinalGroupChatRoom> findLatestFinalRoomByTeamRoomId(Long teamRoomId) {
        List<GFinalGroupChatRoom> finalRooms = finalGroupChatRoomRepository.findActiveRoomsByTeamRoomId(teamRoomId);
        if (!finalRooms.isEmpty()) {
            return finalRooms.stream().findFirst();
        }

        List<GMatchResult> matchResults = matchResultRepository.findByTeamRoomIdAndStatuses(
                teamRoomId,
                ACTIVE_MATCH_STATUSES
        );

        for (GMatchResult matchResult : matchResults) {
            if (matchResult.getFinalGroupChatRoomId() == null) {
                continue;
            }
            Optional<GFinalGroupChatRoom> finalRoom = finalGroupChatRoomRepository.findById(matchResult.getFinalGroupChatRoomId());
            if (finalRoom.isPresent()) {
                return finalRoom;
            }
        }
        return Optional.empty();
    }

    private User findUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND));
    }

    private User findUserWithProfileOrThrow(Long userId) {
        return userRepository.findAllByIdWithProfile(List.of(userId)).stream()
                .findFirst()
                .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND));
    }

    private MatchingCandidateResponse toMatchingCandidateResponse(User user) {
        UserProfile profile = user.getUserProfile();
        UserProfileResponse profileResponse = profile != null ? UserProfileResponse.from(profile, imageUrlBase) : null;

        return MatchingCandidateResponse.builder()
                .userId(user.getId())
                .nickname(user.getNickname())
                .deptName(user.getDeptName())
                .profileImage(profile != null ? toFullImageUrl(profile.getProfileImagePath()) : null)
                .gender(profile != null ? profile.getGender() : null)
                .admissionYear(univ.airconnect.user.domain.AdmissionYear.from(user.getStudentNum()))
                .onboardingStatus(user.getOnboardingStatus())
                .emailVerified(user.hasVerifiedSchoolEmail())
                .profileExists(profile != null)
                .profileImageUploaded(profile != null && profile.getProfileImagePath() != null
                        && !profile.getProfileImagePath().isBlank())
                .age(profile != null ? profile.getAge() : null)
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

    private void validateUserTeamGender(Long userId, GTeamGender teamGender) {
        if (teamGender == null) {
            throw new BusinessException(ErrorCode.TEAM_GENDER_REQUIRED);
        }

        if (resolveUserTeamGender(userId) != teamGender) {
            throw new BusinessException(ErrorCode.TEAM_GENDER_MISMATCH);
        }
    }

    private GTeamGender resolveUserTeamGender(Long userId) {
        UserProfile profile = userProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROFILE_GENDER_REQUIRED));

        if (profile.getGender() == null) {
            throw new BusinessException(ErrorCode.PROFILE_GENDER_REQUIRED);
        }

        return mapUserGenderToTeamGender(profile.getGender());
    }

    private GTeamGender mapUserGenderToTeamGender(Gender gender) {
        return gender == Gender.MALE ? GTeamGender.M : GTeamGender.F;
    }

    private int requiredTicketsFor(GTeamSize teamSize) {
        if (teamSize == null) {
            throw new BusinessException(ErrorCode.TEAM_SIZE_REQUIRED);
        }
        return teamSize.getValue();
    }

    private List<User> lockAndValidateGroupMatchTickets(GTeamSize teamSize, Collection<Long> userIds) {
        int requiredTickets = requiredTicketsFor(teamSize);
        List<User> users = findUsersForUpdateInOrder(userIds);
        for (User user : users) {
            ensureUserHasEnoughGroupMatchTickets(user, requiredTickets, "과팅 매칭 완료 처리 실패");
        }
        return users;
    }

    private void consumeGroupMatchTicketsAfterFinalRoomCreated(GTeamSize teamSize, List<User> users, Long matchResultId) {
        int requiredTickets = requiredTicketsFor(teamSize);
        for (User user : users) {
            int beforeAmount = user.getTickets();
            user.consumeTickets(requiredTickets);
            ticketLedgerRepository.save(TicketLedger.consumeForGroupMatching(
                    user.getId(), requiredTickets, beforeAmount, user.getTickets(), matchResultId));
            log.info(
                    "과팅 매칭 티켓 차감 완료: userId={}, 차감 티켓={}, 남은 티켓={}",
                    user.getId(),
                    requiredTickets,
                    user.getTickets()
            );
        }
    }

    private void ensureUserHasEnoughGroupMatchTickets(User user, int requiredTickets, String logContext) {
        if (user.getTickets() >= requiredTickets) {
            return;
        }

        log.warn(
                "{}: 티켓이 부족합니다. userId={}, 현재 티켓={}, 필요 티켓={}",
                logContext,
                user.getId(),
                user.getTickets(),
                requiredTickets
        );
        throw new BusinessException(
                ErrorCode.INVALID_REQUEST,
                "과팅에 필요한 티켓이 부족합니다. 현재 티켓: " + user.getTickets() + ", 필요 티켓: " + requiredTickets
        );
    }

    private List<User> findUsersForUpdateInOrder(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<Long> uniqueUserIds = userIds.stream()
                .filter(Objects::nonNull)
                .sorted()
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<User> users = new ArrayList<>(uniqueUserIds.size());
        for (Long userId : uniqueUserIds) {
            User user = userRepository.findByIdForTicketUpdate(userId)
                    .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND));
            users.add(user);
        }
        return users;
    }

    private String buildFinalRoomName(GTeamSize teamSize) {
        if (teamSize == null) {
            throw new BusinessException(ErrorCode.TEAM_SIZE_REQUIRED);
        }

        long sequence = finalGroupChatRoomRepository.countByTeamSize(teamSize) + 1;
        String roomName = teamSize.getValue() + ":" + teamSize.getValue() + "그룹매칭방(" + sequence + ")";
        return truncate(roomName, 100);
    }

    private String buildFinalRoomName(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            throw new BusinessException(ErrorCode.GROUP_MATCH_ARGUMENT_INVALID, "최종 채팅방 이름을 만들 멤버가 없습니다.");
        }

        LinkedHashSet<Long> orderedUserIds = userIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<Long, User> userMap = userRepository.findAllById(orderedUserIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user));

        String roomName = orderedUserIds.stream()
                .map(userId -> {
                    User user = userMap.get(userId);
                    if (user == null) {
                        throw new AuthException(AuthErrorCode.USER_NOT_FOUND);
                    }
                    String nickname = user.getNickname();
                    if (nickname == null || nickname.isBlank()) {
                        return "사용자" + userId;
                    }
                    return nickname.trim();
                })
                .collect(Collectors.joining(", "));

        return truncate(roomName, 100);
    }

    private String generateUniqueInviteCode() {
        for (int i = 0; i < 20; i++) {
            String candidate = String.format(java.util.Locale.ROOT, "%06d", INVITE_CODE_RANDOM.nextInt(1_000_000));
            if (!temporaryTeamRoomRepository.existsByInviteCode(candidate)) {
                return candidate;
            }
        }
        throw new BusinessException(ErrorCode.INVITE_CODE_GENERATION_FAILED);
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private QueueReconcileResult reconcileQueueUnderLock(GTeamSize teamSize) {
        if (teamSize == null) {
            throw new BusinessException(ErrorCode.TEAM_SIZE_REQUIRED);
        }

        List<GTemporaryTeamRoom> waitingRooms = temporaryTeamRoomRepository.findAllQueueWaitingRooms(teamSize);
        boolean metadataRecovered = recoverMissingQueueMetadata(waitingRooms);

        List<Long> expectedRoomIds = waitingRooms.stream()
                .map(GTemporaryTeamRoom::getId)
                .collect(Collectors.toList());
        List<Long> currentRoomIds = readQueueRoomIds(teamSize, FULL_QUEUE_SCAN);
        List<Long> distinctCurrentRoomIds = distinctQueueRoomIds(currentRoomIds);

        boolean rebuildRequired = shouldRebuildQueue(expectedRoomIds, distinctCurrentRoomIds);
        if (rebuildRequired) {
            rebuildRedisQueue(teamSize, waitingRooms);
        } else {
            syncQueueTokenMappings(waitingRooms);
        }

        return new QueueReconcileResult(
                teamSize,
                rebuildRequired,
                metadataRecovered,
                true,
                expectedRoomIds.size(),
                distinctCurrentRoomIds.size()
        );
    }

    private boolean recoverMissingQueueMetadata(List<GTemporaryTeamRoom> waitingRooms) {
        boolean metadataRecovered = false;

        for (GTemporaryTeamRoom waitingRoom : waitingRooms) {
            String recoveredQueueToken = waitingRoom.getQueueToken();
            if (recoveredQueueToken == null || recoveredQueueToken.isBlank()) {
                recoveredQueueToken = UUID.randomUUID().toString();
            }

            if (waitingRoom.recoverQueueMetadata(recoveredQueueToken)) {
                metadataRecovered = true;
            }
        }

        return metadataRecovered;
    }

    private boolean shouldRebuildQueue(List<Long> expectedRoomIds, List<Long> currentRoomIds) {
        if (expectedRoomIds.isEmpty()) {
            return false;
        }

        Set<Long> expectedRoomIdSet = new LinkedHashSet<>(expectedRoomIds);
        List<Long> currentExpectedOrder = currentRoomIds.stream()
                .filter(expectedRoomIdSet::contains)
                .collect(Collectors.toList());

        if (currentExpectedOrder.size() != expectedRoomIds.size()) {
            return true;
        }

        return !currentExpectedOrder.equals(expectedRoomIds);
    }

    private void rebuildRedisQueue(GTeamSize teamSize, List<GTemporaryTeamRoom> waitingRooms) {
        redisTemplate.delete(queueKey(teamSize));

        List<Object> queueRoomIds = waitingRooms.stream()
                .map(GTemporaryTeamRoom::getId)
                .map(String::valueOf)
                .collect(Collectors.toList());

        if (!queueRoomIds.isEmpty()) {
            redisTemplate.opsForList().rightPushAll(queueKey(teamSize), queueRoomIds);
        }

        syncQueueTokenMappings(waitingRooms);
    }

    private void syncQueueTokenMappings(List<GTemporaryTeamRoom> waitingRooms) {
        for (GTemporaryTeamRoom waitingRoom : waitingRooms) {
            String queueToken = waitingRoom.getQueueToken();
            if (queueToken == null || queueToken.isBlank()) {
                continue;
            }
            redisTemplate.opsForValue().set(
                    queueTokenKey(queueToken),
                    String.valueOf(waitingRoom.getId()),
                    QUEUE_TOKEN_TTL
            );
        }
    }

    private List<Long> distinctQueueRoomIds(List<Long> roomIds) {
        if (roomIds.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(new LinkedHashSet<>(roomIds));
    }

    /**
     * 큐 구성 변경 후 같은 인원수 대기열에 남은 모든 팀의 최신 순번을 발행한다.
     * 순번은 상대 팀과 섞지 않고 같은 성별 팀 안에서 queuedAt, id 순으로 계산한다.
     */
    private void publishWaitingQueueSnapshots(GTeamSize teamSize) {
        List<GTemporaryTeamRoom> waitingRooms = temporaryTeamRoomRepository.findAllQueueWaitingRooms(teamSize);
        if (waitingRooms.isEmpty()) {
            return;
        }

        Map<GTeamGender, Integer> totals = new EnumMap<>(GTeamGender.class);
        for (GTemporaryTeamRoom room : waitingRooms) {
            totals.merge(room.getTeamGender(), 1, Integer::sum);
        }

        Map<GTeamGender, Integer> positions = new EnumMap<>(GTeamGender.class);
        for (GTemporaryTeamRoom room : waitingRooms) {
            int position = positions.merge(room.getTeamGender(), 1, Integer::sum);
            matchingEventPublisher.publishQueueSnapshot(new QueueSnapshot(
                    room.getId(),
                    room.getStatus().name(),
                    position,
                    position - 1,
                    totals.get(room.getTeamGender()),
                    null,
                    null
            ));
        }
    }

    private String queueKey(GTeamSize teamSize) {
        return "matching:queue:" + teamSize.name();
    }

    private String queueTokenKey(String queueToken) {
        return "matching:queue:token:" + queueToken;
    }

    private String processLockKey(GTeamSize teamSize) {
        return "matching:queue:process-lock:" + teamSize.name();
    }

    private void enqueueRoom(GTeamSize teamSize, Long teamRoomId, String queueToken) {
        removeRoomFromRedisQueue(teamSize, teamRoomId, null);
        redisTemplate.opsForList().rightPush(queueKey(teamSize), String.valueOf(teamRoomId));
        redisTemplate.opsForValue().set(queueTokenKey(queueToken), String.valueOf(teamRoomId), QUEUE_TOKEN_TTL);
    }

    private void removeRoomFromRedisQueue(GTeamSize teamSize, Long teamRoomId, String queueToken) {
        redisTemplate.opsForList().remove(queueKey(teamSize), 0, String.valueOf(teamRoomId));
        if (queueToken != null && !queueToken.isBlank()) {
            redisTemplate.delete(queueTokenKey(queueToken));
        }
    }

    @SuppressWarnings("unchecked")
    private List<Long> readQueueRoomIds(GTeamSize teamSize, int limit) {
        List<Object> values;
        if (limit > 0) {
            values = (List<Object>) (List<?>) redisTemplate.opsForList().range(queueKey(teamSize), 0, limit - 1);
        } else {
            values = (List<Object>) (List<?>) redisTemplate.opsForList().range(queueKey(teamSize), 0, -1);
        }

        if (values == null || values.isEmpty()) {
            return List.of();
        }

        List<Long> result = new ArrayList<>();
        for (Object value : values) {
            if (value == null) {
                continue;
            }
            try {
                result.add(Long.valueOf(String.valueOf(value)));
            } catch (NumberFormatException e) {
                log.warn("큐에서 해석할 수 없는 값이 발견되었습니다. value={}", value);
            }
        }
        return result;
    }

    private int findQueuePosition(List<Long> queueRoomIds, Long teamRoomId) {
        for (int i = 0; i < queueRoomIds.size(); i++) {
            if (Objects.equals(queueRoomIds.get(i), teamRoomId)) {
                return i;
            }
        }
        return -1;
    }

    private <T> Optional<T> withQueueLock(GTeamSize teamSize, Supplier<T> action) {
        if (teamSize == null) {
            throw new BusinessException(ErrorCode.TEAM_SIZE_REQUIRED);
        }
        String processLockKey = processLockKey(teamSize);
        String lockValue = UUID.randomUUID().toString();

        if (!tryAcquireProcessLock(processLockKey, lockValue)) {
            return Optional.empty();
        }

        try {
            return Optional.ofNullable(action.get());
        } finally {
            safelyReleaseProcessLock(processLockKey, lockValue);
        }
    }

    private <T> T withQueueLockOrThrow(GTeamSize teamSize, Supplier<T> action) {
        if (teamSize == null) {
            throw new BusinessException(ErrorCode.TEAM_SIZE_REQUIRED);
        }
        String processLockKey = processLockKey(teamSize);
        String lockValue = UUID.randomUUID().toString();

        if (!tryAcquireProcessLockWithRetry(processLockKey, lockValue)) {
            throw new BusinessException(ErrorCode.QUEUE_LOCK_FAILED);
        }

        try {
            return action.get();
        } finally {
            safelyReleaseProcessLock(processLockKey, lockValue);
        }
    }

    private boolean tryAcquireProcessLockWithRetry(String lockKey, String lockValue) {
        for (int attempt = 0; attempt < PROCESS_LOCK_RETRY_COUNT; attempt++) {
            if (tryAcquireProcessLock(lockKey, lockValue)) {
                return true;
            }

            try {
                Thread.sleep(PROCESS_LOCK_RETRY_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    private boolean tryAcquireProcessLock(String lockKey, String lockValue) {
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, MATCH_PROCESS_LOCK_TTL);
        return Boolean.TRUE.equals(acquired);
    }

    private void safelyReleaseProcessLock(String lockKey, String lockValue) {
        // 소유권 비교와 삭제를 원자화해 TTL 만료 후 재획득한 다른 worker의 lock을 보호한다.
        redisTemplate.execute(RELEASE_PROCESS_LOCK_SCRIPT, List.of(lockKey), lockValue);
    }

    public record QueueSnapshot(
            Long teamRoomId,
            String status,
            int position,
            int aheadCount,
            int totalWaitingTeams,
            Long finalGroupRoomId,
            Long finalChatRoomId
    ) {
        public static QueueSnapshot statusOnly(Long teamRoomId, String status) {
            return new QueueSnapshot(teamRoomId, status, 0, 0, 0, null, null);
        }

        public static QueueSnapshot matched(Long teamRoomId, Long finalGroupRoomId, Long finalChatRoomId) {
            return new QueueSnapshot(teamRoomId, GTemporaryTeamRoomStatus.CLOSED.name(), 0, 0, 0, finalGroupRoomId, finalChatRoomId);
        }
    }

    public record MatchSuccessResult(
            Long matchResultId,
            Long finalGroupRoomId,
            Long finalChatRoomId,
            Long firstTeamRoomId,
            Long secondTeamRoomId
    ) {
        public boolean contains(Long teamRoomId) {
            return Objects.equals(firstTeamRoomId, teamRoomId) || Objects.equals(secondTeamRoomId, teamRoomId);
        }
    }

    public record QueueReconcileResult(
            GTeamSize teamSize,
            boolean rebuilt,
            boolean metadataRecovered,
            boolean lockAcquired,
            int waitingTeamCount,
            int redisQueueCount
    ) {
        public static QueueReconcileResult lockBusy(GTeamSize teamSize) {
            return new QueueReconcileResult(teamSize, false, false, false, 0, 0);
        }
    }
}






