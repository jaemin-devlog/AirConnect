package univ.airconnect.groupmatching.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import univ.airconnect.auth.exception.AuthErrorCode;
import univ.airconnect.auth.exception.AuthException;
import univ.airconnect.chat.domain.entity.ChatRoom;
import univ.airconnect.chat.domain.entity.ChatRoomMember;
import univ.airconnect.chat.repository.ChatRoomMemberRepository;
import univ.airconnect.chat.service.ChatService;
import univ.airconnect.global.error.BusinessException;
import univ.airconnect.global.error.ErrorCode;
import univ.airconnect.groupmatching.domain.GMatchResultStatus;
import univ.airconnect.groupmatching.domain.GTeamGender;
import univ.airconnect.groupmatching.domain.GTeamSize;
import univ.airconnect.groupmatching.domain.GTeamVisibility;
import univ.airconnect.groupmatching.domain.GTemporaryTeamRoomStatus;
import univ.airconnect.groupmatching.domain.GGenderFilter;
import univ.airconnect.groupmatching.domain.entity.GFinalGroupChatRoom;
import univ.airconnect.groupmatching.domain.entity.GMatchResult;
import univ.airconnect.groupmatching.domain.entity.GTeamReadyState;
import univ.airconnect.groupmatching.domain.entity.GTemporaryTeamMember;
import univ.airconnect.groupmatching.domain.entity.GTemporaryTeamRoom;
import univ.airconnect.groupmatching.repository.GFinalGroupChatRoomRepository;
import univ.airconnect.groupmatching.repository.GMatchResultRepository;
import univ.airconnect.groupmatching.repository.GTeamReadyStateRepository;
import univ.airconnect.groupmatching.repository.GTemporaryTeamMemberRepository;
import univ.airconnect.groupmatching.repository.GTemporaryTeamRoomRepository;
import univ.airconnect.notification.domain.NotificationType;
import univ.airconnect.notification.service.NotificationService;
import univ.airconnect.user.domain.Gender;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.domain.entity.UserProfile;
import univ.airconnect.user.repository.UserProfileRepository;
import univ.airconnect.user.repository.UserRepository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
    private static final Duration MATCH_PROCESS_LOCK_TTL = Duration.ofSeconds(5);
    private static final Duration QUEUE_TOKEN_TTL = Duration.ofHours(12);
    private static final int PROCESS_LOCK_RETRY_COUNT = 20;
    private static final long PROCESS_LOCK_RETRY_DELAY_MS = 50L;

    private final GTemporaryTeamRoomRepository temporaryTeamRoomRepository;
    private final GTemporaryTeamMemberRepository temporaryTeamMemberRepository;
    private final GTeamReadyStateRepository teamReadyStateRepository;
    private final GMatchResultRepository matchResultRepository;
    private final GFinalGroupChatRoomRepository finalGroupChatRoomRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final ChatService chatService;
    private final GMatchingEventPublisher matchingEventPublisher;
    private final GMatchingPushService matchingPushService;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * Step 1. 임시 팀방을 생성한다.
     * - 팀 생성과 동시에 팀 전용 임시 채팅방도 함께 만든다.
     * - 방장 멤버십, 채팅방 멤버십, 준비 상태를 같은 트랜잭션에서 저장한다.
     */
    @Transactional
    public GTemporaryTeamRoom createTemporaryTeamRoom(
            Long leaderUserId,
            String teamName,
            GTeamGender teamGender,
            GTeamSize teamSize,
            GGenderFilter opponentGenderFilter,
            GTeamVisibility visibility
    ) {
        User leader = findUserOrThrow(leaderUserId);
        ensureUserHasNoActiveTeamRoom(leaderUserId);
        validateUserTeamGender(leaderUserId, teamGender);

        String tempChatRoomName = buildTempRoomName(teamName, teamSize);
        ChatRoom tempChatRoom = chatService.createGroupRoomWithMembers(tempChatRoomName, List.of(leaderUserId));

        GTemporaryTeamRoom teamRoom = GTemporaryTeamRoom.create(
                leaderUserId,
                teamName,
                teamGender,
                teamSize,
                opponentGenderFilter,
                visibility,
                tempChatRoom.getId()
        );
        teamRoom.assignInviteCode(generateUniqueInviteCode());
        temporaryTeamRoomRepository.save(teamRoom);

        temporaryTeamMemberRepository.save(GTemporaryTeamMember.create(teamRoom.getId(), leaderUserId, true));
        teamReadyStateRepository.save(GTeamReadyState.create(teamRoom.getId(), leaderUserId));

        chatService.publishEnterMessage(
                tempChatRoom.getId(),
                leaderUserId,
                leader.getNickname() + "님이 팀방을 생성했습니다."
        );

        return teamRoom;
    }

    /** 공개 모집 중인 임시 팀방 목록을 조회한다. */

    @Transactional(readOnly = true)
    public List<GTemporaryTeamRoom> findRecruitablePublicRooms(GTeamSize teamSize) {
        return temporaryTeamRoomRepository.findRecruitablePublicRooms(
                        GTeamVisibility.PUBLIC,
                        GTemporaryTeamRoomStatus.OPEN,
                        teamSize
                ).stream()
                .filter(room -> !room.isFull())
                .collect(Collectors.toList());
    }

    /**
     * 공개방에 입장한다.
     */
    @Transactional
    public GTemporaryTeamRoom joinPublicRoom(Long teamRoomId, Long userId) {
        GTemporaryTeamRoom teamRoom = temporaryTeamRoomRepository.findByIdForUpdate(teamRoomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEAM_ROOM_NOT_FOUND));

        if (!teamRoom.isPublicRoom()) {
            throw new BusinessException(ErrorCode.PUBLIC_ROOM_ONLY);
        }

        return joinTeamRoomInternal(teamRoom, userId);
    }


    /**
     * 초대 코드로 임시 팀방에 입장한다.
     */
    @Transactional
    public GTemporaryTeamRoom joinRoomByInviteCode(String inviteCode, Long userId) {
        if (inviteCode == null || inviteCode.isBlank()) {
            throw new BusinessException(ErrorCode.INVITE_CODE_REQUIRED);
        }

        GTemporaryTeamRoom teamRoom = temporaryTeamRoomRepository.findByInviteCode(inviteCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INVITE_CODE));

        teamRoom = temporaryTeamRoomRepository.findByIdForUpdate(teamRoom.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.TEAM_ROOM_NOT_FOUND));

        return joinTeamRoomInternal(teamRoom, userId);
    }

    @Transactional
    public GTemporaryTeamRoom generateInviteCode(Long teamRoomId, Long requestUserId) {
        GTemporaryTeamRoom teamRoom = temporaryTeamRoomRepository.findByIdForUpdate(teamRoomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEAM_ROOM_NOT_FOUND));

        validateActiveMembership(teamRoomId, requestUserId);
        teamRoom.assignInviteCode(generateUniqueInviteCode());
        return teamRoom;
    }

    /**
     * 팀원의 준비 상태를 변경한다.
     * - READY_CHECK 상태에서만 사용할 수 있다.
     */
    @Transactional
    public GTeamReadyState updateReadyState(Long teamRoomId, Long userId, boolean ready) {
        GTemporaryTeamRoom teamRoom = getTeamRoomForMemberAction(teamRoomId, userId);

        if (teamRoom.getStatus() != GTemporaryTeamRoomStatus.READY_CHECK) {
            throw new BusinessException(ErrorCode.READY_CHECK_REQUIRED);
        }

        GTeamReadyState readyState = teamReadyStateRepository.findByTeamRoomIdAndUserId(teamRoomId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.READY_STATE_NOT_FOUND));

        boolean wasReady = readyState.isReady();
        readyState.setReady(ready);
        matchingEventPublisher.publishStatus(teamRoomId, teamRoom.getStatus().name());

        boolean allReady = false;
        if (!wasReady && ready) {
            allReady = teamReadyStateRepository.areAllMembersReady(teamRoomId, teamRoom.getTeamSize().getValue());
        }

        if (wasReady != ready) {
            Long skipRecipientId = allReady ? teamRoom.getLeaderId() : null;
            notifyTeamMemberReadyChanged(teamRoom, userId, ready, skipRecipientId);
        }

        if (allReady) {
            notifyTeamAllReady(teamRoom, userId, readyState.getUpdatedAt());
        }
        return readyState;
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

        validateActiveMembership(teamRoomId, requestUserId);

        boolean allReady = teamReadyStateRepository.areAllMembersReady(teamRoomId, teamRoom.getTeamSize().getValue());
        String queueToken = UUID.randomUUID().toString();

        teamRoom.startQueue(requestUserId, allReady, queueToken);

        QueueSnapshot snapshot = withQueueLockOrThrow(teamRoom.getTeamSize(), () -> {
            enqueueRoom(teamRoom.getTeamSize(), teamRoom.getId(), queueToken);
            processQueueUntilStableUnderLock(teamRoom.getTeamSize());
            return buildQueueSnapshot(teamRoom);
        });
        if (snapshot.finalGroupRoomId() == null) {
            matchingEventPublisher.publishQueueSnapshot(snapshot);
        }
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

        String queueToken = teamRoom.getQueueToken();
        teamRoom.leaveQueue();

        withQueueLockOrThrow(teamRoom.getTeamSize(), () -> {
            removeRoomFromRedisQueue(teamRoom.getTeamSize(), teamRoom.getId(), queueToken);
            return null;
        });
        matchingEventPublisher.publishStatus(teamRoomId, teamRoom.getStatus().name());
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
        int matchedCount = 0;

        while (true) {
            MatchSuccessResult matchResult = processQueue(teamSize, FULL_QUEUE_SCAN);
            if (matchResult == null) {
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

        List<Long> queueRoomIds = distinctQueueRoomIds(readQueueRoomIds(teamRoom.getTeamSize(), -1));
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
        return withQueueLock(teamSize, () -> processQueueUnderLock(teamSize, scanSize)).orElse(null);
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
        Long tempChatRoomId = teamRoom.getTempChatRoomId();
        GTemporaryTeamRoomStatus status = teamRoom.getStatus();

        if (chatService.isMember(tempChatRoomId, userId)) {
            chatService.publishExitMessage(
                    tempChatRoomId,
                    userId,
                    user.getNickname() + "님이 팀에서 나갔습니다."
            );
        } else {
            log.warn("Skip exit-message publishing because chat membership is already missing. teamRoomId={}, userId={}, chatRoomId={}",
                    teamRoomId, userId, tempChatRoomId);
        }

        removeChatRoomMembership(tempChatRoomId, userId);
        member.markLeft();
        teamReadyStateRepository.findByTeamRoomIdAndUserId(teamRoomId, userId)
                .ifPresent(teamReadyStateRepository::delete);

        if (status.canModifyMembers()) {
            if (teamRoom.getCurrentMemberCount() > 1) {
                teamRoom.removeMember();
                resetReadyStatesForActiveMembers(teamRoomId);
            } else {
                log.warn("Skip member-count decrement due to unexpected count while leaving team room. teamRoomId={}, userId={}, currentMemberCount={}",
                        teamRoomId, userId, teamRoom.getCurrentMemberCount());
            }
        } else {
            log.warn("Skip member-count decrement because room status no longer allows member modification. teamRoomId={}, userId={}, status={}",
                    teamRoomId, userId, status);
        }

        notifyTeamMemberLeft(teamRoom, userId, user.getNickname());
        matchingEventPublisher.publishStatus(teamRoomId, teamRoom.getStatus().name());

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
        Long tempChatRoomId = teamRoom.getTempChatRoomId();

        if (chatService.isMember(tempChatRoomId, requestUserId)) {
            chatService.publishExitMessage(
                    tempChatRoomId,
                    requestUserId,
                    leader.getNickname() + "님이 팀을 해산했습니다."
            );
        } else {
            log.warn("Skip room-cancel exit-message publishing because leader chat membership is missing. teamRoomId={}, leaderId={}, chatRoomId={}",
                    teamRoomId, requestUserId, tempChatRoomId);
        }

        teamRoom.cancel(requestUserId);
        matchingEventPublisher.publishStatus(teamRoomId, teamRoom.getStatus().name());

        List<GTemporaryTeamMember> activeMembers = temporaryTeamMemberRepository
                .findByTeamRoomIdAndLeftAtIsNullOrderByJoinedAtAsc(teamRoomId);
        notifyTeamRoomCancelled(teamRoom, activeMembers, requestUserId, leader.getNickname());
        markMembersLeft(activeMembers);
        removeChatRoomMemberships(tempChatRoomId, extractUserIds(activeMembers));
        teamReadyStateRepository.deleteByTeamRoomId(teamRoomId);
        withQueueLockOrThrow(teamRoom.getTeamSize(), () -> {
            removeRoomFromRedisQueue(teamRoom.getTeamSize(), teamRoomId, queueToken);
            return null;
        });

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

    @Transactional(readOnly = true)
    public Long getTempChatRoomId(Long teamRoomId, Long requestUserId) {
        GTemporaryTeamRoom teamRoom = temporaryTeamRoomRepository.findById(teamRoomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEAM_ROOM_NOT_FOUND));
        validateActiveMembership(teamRoomId, requestUserId);
        return teamRoom.getTempChatRoomId();
    }

    private GTemporaryTeamRoom joinTeamRoomInternal(GTemporaryTeamRoom teamRoom, Long userId) {
        User user = findUserOrThrow(userId);
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
        }

        teamRoom.addMember();
        if (existingMember.isPresent()) {
            existingMember.get().rejoin();
        } else {
            temporaryTeamMemberRepository.save(GTemporaryTeamMember.create(teamRoom.getId(), userId, false));
        }

        Optional<GTeamReadyState> existingReadyState =
                teamReadyStateRepository.findByTeamRoomIdAndUserId(teamRoom.getId(), userId);
        if (existingReadyState.isPresent()) {
            existingReadyState.get().markNotReady();
        } else {
            teamReadyStateRepository.save(GTeamReadyState.create(teamRoom.getId(), userId));
        }
        resetReadyStatesForActiveMembers(teamRoom.getId());

        boolean becameReadyCheck = false;
        if (teamRoom.isFull()) {
            teamRoom.enterReadyCheck(teamRoom.getLeaderId());
            becameReadyCheck = true;
        }

        chatService.addMembersToRoom(teamRoom.getTempChatRoomId(), List.of(userId));
        chatService.publishEnterMessage(
                teamRoom.getTempChatRoomId(),
                userId,
                user.getNickname() + "님이 팀에 입장했습니다."
        );

        notifyTeamMemberJoined(teamRoom, userId, user.getNickname());
        if (becameReadyCheck) {
            notifyTeamReadyRequired(teamRoom);
        }
        matchingEventPublisher.publishStatus(teamRoom.getId(), teamRoom.getStatus().name());

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

        LinkedHashSet<Long> finalMemberIds = new LinkedHashSet<>();
        finalMemberIds.addAll(extractUserIds(firstMembers));
        finalMemberIds.addAll(extractUserIds(secondMembers));

        ChatRoom finalChatRoom = chatService.createGroupRoomWithMembers(
                buildFinalRoomName(first, second),
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

        matchResult.completeFinalRoomCreation(finalGroupChatRoom.getId());
        QueueSnapshot firstMatchedSnapshot = QueueSnapshot.matched(first.getId(), finalGroupChatRoom.getId(), finalChatRoom.getId());
        QueueSnapshot secondMatchedSnapshot = QueueSnapshot.matched(second.getId(), finalGroupChatRoom.getId(), finalChatRoom.getId());
        matchingEventPublisher.publishMatched(firstMatchedSnapshot);
        matchingEventPublisher.publishMatched(secondMatchedSnapshot);
        notifyGroupMatched(finalMemberIds, first.getId(), second.getId(), finalGroupChatRoom.getId(), finalChatRoom.getId());
        matchingPushService.notifyMatched(finalMemberIds, finalGroupChatRoom.getId(), finalChatRoom.getId());

        chatService.publishEnterMessage(
                first.getTempChatRoomId(),
                first.getLeaderId(),
                "매칭이 완료되었습니다. 최종 그룹 채팅방으로 이동합니다."
        );
        chatService.publishEnterMessage(
                second.getTempChatRoomId(),
                second.getLeaderId(),
                "매칭이 완료되었습니다. 최종 그룹 채팅방으로 이동합니다."
        );
        chatService.publishEnterMessage(
                finalChatRoom.getId(),
                first.getLeaderId(),
                "매칭이 완료되었습니다. 최종 그룹 채팅방이 생성되었습니다."
        );

        markMembersLeft(firstMembers);
        markMembersLeft(secondMembers);

        first.closeAfterFinalRoomCreated();
        second.closeAfterFinalRoomCreated();

        removeChatRoomMemberships(first.getTempChatRoomId(), extractUserIds(firstMembers));
        removeChatRoomMemberships(second.getTempChatRoomId(), extractUserIds(secondMembers));
        teamReadyStateRepository.deleteByTeamRoomId(first.getId());
        teamReadyStateRepository.deleteByTeamRoomId(second.getId());
        removeRoomFromRedisQueue(first.getTeamSize(), first.getId(), firstQueueToken);
        removeRoomFromRedisQueue(second.getTeamSize(), second.getId(), secondQueueToken);

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

        for (Long recipientId : recipientIds) {
            sendGroupNotification(
                    recipientId,
                    NotificationType.TEAM_MEMBER_JOINED,
                    "팀원 합류",
                    joinedNickname + "님이 팀에 합류했어요.",
                    teamRoomDeeplink(teamRoom.getId()),
                    joinedUserId,
                    payload.toString(),
                    null,
                    true
            );
        }
    }

    private void notifyTeamReadyRequired(GTemporaryTeamRoom teamRoom) {
        List<Long> recipientIds = temporaryTeamMemberRepository.findByTeamRoomIdAndLeftAtIsNullOrderByJoinedAtAsc(teamRoom.getId())
                .stream()
                .map(GTemporaryTeamMember::getUserId)
                .distinct()
                .toList();
        if (recipientIds.isEmpty()) {
            return;
        }

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("teamRoomId", teamRoom.getId());
        payload.put("teamSize", teamRoom.getTeamSize().name());
        payload.put("currentMemberCount", teamRoom.getCurrentMemberCount());

        String dedupeKey = buildGroupMatchingDedupeKey(
                "team-ready-required",
                teamRoom.getId(),
                teamRoom.getUpdatedAt() != null ? teamRoom.getUpdatedAt() : LocalDateTime.now()
        );

        for (Long recipientId : recipientIds) {
            sendGroupNotification(
                    recipientId,
                    NotificationType.TEAM_READY_REQUIRED,
                    "팀 준비 확인 필요",
                    "팀원이 모두 모였어요. 준비 상태를 체크해주세요.",
                    teamRoomDeeplink(teamRoom.getId()),
                    null,
                    payload.toString(),
                    dedupeKey,
                    true
            );
        }
    }

    private void notifyTeamMemberReadyChanged(
            GTemporaryTeamRoom teamRoom,
            Long updatedUserId,
            boolean ready,
            Long skipRecipientId
    ) {
        List<GTemporaryTeamMember> activeMembers =
                temporaryTeamMemberRepository.findByTeamRoomIdAndLeftAtIsNullOrderByJoinedAtAsc(teamRoom.getId());
        if (activeMembers == null || activeMembers.isEmpty()) {
            return;
        }

        List<Long> recipientIds = activeMembers.stream()
                .map(GTemporaryTeamMember::getUserId)
                .filter(id -> !Objects.equals(id, updatedUserId))
                .filter(id -> !Objects.equals(id, skipRecipientId))
                .distinct()
                .toList();
        if (recipientIds.isEmpty()) {
            return;
        }

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("teamRoomId", teamRoom.getId());
        payload.put("updatedUserId", updatedUserId);
        payload.put("ready", ready);
        payload.put("status", teamRoom.getStatus().name());
        payload.put("currentMemberCount", teamRoom.getCurrentMemberCount());
        String title = ready ? "팀원 준비 완료" : "팀원 준비 취소";
        String body = ready ? "팀원이 준비를 완료했어요." : "팀원이 준비를 취소했어요.";

        for (Long recipientId : recipientIds) {
            sendGroupNotification(
                    recipientId,
                    NotificationType.TEAM_MEMBER_READY_CHANGED,
                    title,
                    body,
                    teamRoomDeeplink(teamRoom.getId()),
                    updatedUserId,
                    payload.toString(),
                    null,
                    true
            );
        }
    }

    private void notifyTeamAllReady(GTemporaryTeamRoom teamRoom, Long updatedByUserId, LocalDateTime readyChangedAt) {
        Long leaderId = teamRoom.getLeaderId();
        if (leaderId == null || Objects.equals(leaderId, updatedByUserId)) {
            return;
        }

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("teamRoomId", teamRoom.getId());
        payload.put("leaderUserId", leaderId);
        payload.put("allMembersReady", true);

        String dedupeKey = buildGroupMatchingDedupeKey(
                "team-all-ready",
                teamRoom.getId(),
                readyChangedAt != null ? readyChangedAt : LocalDateTime.now()
        );

        sendGroupNotification(
                leaderId,
                NotificationType.TEAM_ALL_READY,
                "모든 팀원이 준비 완료",
                "지금 매칭을 시작할 수 있어요.",
                teamRoomDeeplink(teamRoom.getId()),
                null,
                payload.toString(),
                dedupeKey,
                true
        );
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
                    false
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
            log.error("Group matching notification failed. type={}, recipientUserId={}", type, recipientUserId, e);
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

        Optional<GTemporaryTeamRoom> roomOpt = temporaryTeamRoomRepository.findById(teamRoomId);
        if (roomOpt.isEmpty()) {
            return false;
        }

        GTemporaryTeamRoomStatus status = roomOpt.get().getStatus();
        return status == GTemporaryTeamRoomStatus.MATCHED || status == GTemporaryTeamRoomStatus.CLOSED;
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

        if (room.getStatus() != GTemporaryTeamRoomStatus.CLOSED && room.getStatus() != GTemporaryTeamRoomStatus.MATCHED) {
            throw new BusinessException(ErrorCode.TEAM_ROOM_ACCESS_FORBIDDEN);
        }
    }

    private GTemporaryTeamRoom getTeamRoomForMemberAction(Long teamRoomId, Long userId) {
        GTemporaryTeamRoom teamRoom = temporaryTeamRoomRepository.findByIdForUpdate(teamRoomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEAM_ROOM_NOT_FOUND));
        validateActiveMembership(teamRoomId, userId);
        return teamRoom;
    }

    private void ensureUserHasNoActiveTeamRoom(Long userId) {
        List<GTemporaryTeamRoom> activeRooms = temporaryTeamRoomRepository.findActiveRoomsByUserId(userId, ACTIVE_ROOM_STATUSES);
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

    private void resetReadyStatesForActiveMembers(Long teamRoomId) {
        List<GTeamReadyState> readyStates = teamReadyStateRepository.findByTeamRoomIdOrderByIdAsc(teamRoomId);
        for (GTeamReadyState readyState : readyStates) {
            readyState.markNotReady();
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

    private void removeChatRoomMemberships(Long chatRoomId, Collection<Long> userIds) {
        for (Long userId : userIds) {
            removeChatRoomMembership(chatRoomId, userId);
        }
    }

    private void removeChatRoomMembership(Long chatRoomId, Long userId) {
        Optional<ChatRoomMember> memberOpt = chatRoomMemberRepository.findByChatRoomIdAndUserId(chatRoomId, userId);
        memberOpt.ifPresent(chatRoomMemberRepository::delete);
    }

    private User findUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND));
    }

    private void validateUserTeamGender(Long userId, GTeamGender teamGender) {
        if (teamGender == null) {
            throw new BusinessException(ErrorCode.TEAM_GENDER_REQUIRED);
        }

        UserProfile profile = userProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROFILE_GENDER_REQUIRED));

        if (profile.getGender() == null) {
            throw new BusinessException(ErrorCode.PROFILE_GENDER_REQUIRED);
        }

        GTeamGender userTeamGender = mapUserGenderToTeamGender(profile.getGender());
        if (userTeamGender != teamGender) {
            throw new BusinessException(ErrorCode.TEAM_GENDER_MISMATCH);
        }
    }

    private GTeamGender mapUserGenderToTeamGender(Gender gender) {
        return gender == Gender.MALE ? GTeamGender.M : GTeamGender.F;
    }

    private String buildTempRoomName(String teamName, GTeamSize teamSize) {
        String baseName = (teamName == null || teamName.isBlank()) ? "Temporary Team Room" : teamName.trim();
        String roomName = baseName + " (" + teamSize.getValue() + " team)";
        return truncate(roomName, 100);
    }

    private String buildFinalRoomName(GTemporaryTeamRoom first, GTemporaryTeamRoom second) {
        String prefix = first.getTeamSize().getValue() + ":" + second.getTeamSize().getValue() + " Matched Room";
        String roomName = prefix + " - " + first.getTeamName() + " & " + second.getTeamName();
        return truncate(roomName, 100);
    }

    private String generateUniqueInviteCode() {
        for (int i = 0; i < 20; i++) {
            String candidate = UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
            if (!temporaryTeamRoomRepository.existsUsableInviteCode(candidate)) {
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
        Object current = redisTemplate.opsForValue().get(lockKey);
        if (current != null && Objects.equals(String.valueOf(current), lockValue)) {
            redisTemplate.delete(lockKey);
        }
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





