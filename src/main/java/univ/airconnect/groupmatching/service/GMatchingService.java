package univ.airconnect.groupmatching.service;

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
import univ.airconnect.user.domain.Gender;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.domain.entity.UserProfile;
import univ.airconnect.user.repository.UserProfileRepository;
import univ.airconnect.user.repository.UserRepository;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
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

    private static final int DEFAULT_MATCH_SCAN_SIZE = 50;
    private static final Duration MATCH_PROCESS_LOCK_TTL = Duration.ofSeconds(5);
    private static final Duration QUEUE_TOKEN_TTL = Duration.ofHours(12);

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
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * Step 1. ?꾩떆 ?諛??앹꽦
     * - ?앹꽦 利됱떆 ?꾩떆 ? 梨꾪똿諛⑸룄 媛숈씠 留뚮뱺??
     * - 由щ뜑瑜??꾩떆諛?硫ㅻ쾭 + 梨꾪똿諛?硫ㅻ쾭 + 以鍮꾩긽??row濡??깅줉?쒕떎.
     */
    @Transactional
    public GTemporaryTeamRoom createTemporaryTeamRoom(
            Long leaderUserId,
            String teamName,
            GTeamGender teamGender,
            GTeamSize teamSize,
            GGenderFilter opponentGenderFilter,
            GTeamVisibility visibility,
            Integer ageRangeMin,
            Integer ageRangeMax
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
                tempChatRoom.getId(),
                ageRangeMin,
                ageRangeMax
        );
        temporaryTeamRoomRepository.save(teamRoom);

        temporaryTeamMemberRepository.save(GTemporaryTeamMember.create(teamRoom.getId(), leaderUserId, true));
        teamReadyStateRepository.save(GTeamReadyState.create(teamRoom.getId(), leaderUserId));

        chatService.publishEnterMessage(
                tempChatRoom.getId(),
                leaderUserId,
                leader.getNickname() + "?섏씠 ????앹꽦?덉뒿?덈떎."
        );

        return teamRoom;
    }

    /**
     * 怨듦컻 紐⑥쭛 以묒씤 ?꾩떆諛?紐⑸줉 議고쉶
     */
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
     * 怨듦컻諛??낆옣
     */
    @Transactional
    public GTemporaryTeamRoom joinPublicRoom(Long teamRoomId, Long userId) {
        GTemporaryTeamRoom teamRoom = temporaryTeamRoomRepository.findByIdForUpdate(teamRoomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REQUEST, "?꾩떆 ?諛⑹쓣 李얠쓣 ???놁뒿?덈떎."));

        if (!teamRoom.isPublicRoom()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "怨듦컻諛⑸쭔 怨듦컻 ?낆옣??媛?ν빀?덈떎.");
        }

        return joinTeamRoomInternal(teamRoom, userId);
    }

    /**
     * 鍮꾧났媛쒕갑 珥덈?肄붾뱶 ?낆옣
     */
    @Transactional
    public GTemporaryTeamRoom joinPrivateRoomByInviteCode(String inviteCode, Long userId) {
        if (inviteCode == null || inviteCode.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "珥덈? 肄붾뱶??鍮꾩뼱 ?덉쓣 ???놁뒿?덈떎.");
        }

        GTemporaryTeamRoom teamRoom = temporaryTeamRoomRepository.findByInviteCode(inviteCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REQUEST, "?좏슚?섏? ?딆? 珥덈? 肄붾뱶?낅땲??"));

        teamRoom = temporaryTeamRoomRepository.findByIdForUpdate(teamRoom.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REQUEST, "?꾩떆 ?諛⑹쓣 李얠쓣 ???놁뒿?덈떎."));

        if (!teamRoom.isPrivateRoom()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "鍮꾧났媛쒕갑 珥덈?肄붾뱶留??ъ슜?????덉뒿?덈떎.");
        }

        return joinTeamRoomInternal(teamRoom, userId);
    }

    /**
     * ???以鍮??곹깭 蹂寃?
     * - READY_CHECK ?곹깭?먯꽌留??덉슜
     */
    @Transactional
    public GTeamReadyState updateReadyState(Long teamRoomId, Long userId, boolean ready) {
        GTemporaryTeamRoom teamRoom = getTeamRoomForMemberAction(teamRoomId, userId);

        if (teamRoom.getStatus() != GTemporaryTeamRoomStatus.READY_CHECK) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "以鍮??뺤씤 ?곹깭?먯꽌留?以鍮??щ?瑜?蹂寃쏀븷 ???덉뒿?덈떎.");
        }

        GTeamReadyState readyState = teamReadyStateRepository.findByTeamRoomIdAndUserId(teamRoomId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REQUEST, "以鍮??곹깭 ?뺣낫瑜?李얠쓣 ???놁뒿?덈떎."));

        readyState.setReady(ready);
        return readyState;
    }

    /**
     * Step 3. 諛⑹옣??留ㅼ묶 ?쒖옉 -> Redis ???깅줉
     * - DB ?곹깭瑜?QUEUE_WAITING ?쇰줈 諛붽씀怨?
     * - Redis 由ъ뒪?몄뿉 teamRoomId 瑜??ｋ뒗??
     * - 吏곹썑 利됱떆 ??踰?留ㅼ묶 ?쒕룄瑜??섑뻾?쒕떎.
     */
    @Transactional
    public QueueSnapshot startMatching(Long teamRoomId, Long requestUserId) {
        GTemporaryTeamRoom teamRoom = temporaryTeamRoomRepository.findByIdForUpdate(teamRoomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REQUEST, "?꾩떆 ?諛⑹쓣 李얠쓣 ???놁뒿?덈떎."));

        validateActiveMembership(teamRoomId, requestUserId);

        boolean allReady = teamReadyStateRepository.areAllMembersReady(teamRoomId, teamRoom.getTeamSize().getValue());
        String queueToken = UUID.randomUUID().toString();

        teamRoom.startQueue(requestUserId, allReady, queueToken);

        enqueueRoom(teamRoom.getTeamSize(), teamRoom.getId(), queueToken);
        MatchSuccessResult matchResult = processQueue(teamRoom.getTeamSize(), DEFAULT_MATCH_SCAN_SIZE);

        if (matchResult != null && matchResult.contains(teamRoomId)) {
            return QueueSnapshot.matched(teamRoomId, matchResult.finalGroupRoomId(), matchResult.finalChatRoomId());
        }

        QueueSnapshot snapshot = getQueueSnapshot(teamRoomId, requestUserId);
        matchingEventPublisher.publishQueueSnapshot(snapshot);
        return snapshot;
    }

    /**
     * ???댄깉
     */
    @Transactional
    public GTemporaryTeamRoom leaveMatchingQueue(Long teamRoomId, Long requestUserId) {
        GTemporaryTeamRoom teamRoom = temporaryTeamRoomRepository.findByIdForUpdate(teamRoomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REQUEST, "?꾩떆 ?諛⑹쓣 李얠쓣 ???놁뒿?덈떎."));

        validateActiveMembership(teamRoomId, requestUserId);

        String queueToken = teamRoom.getQueueToken();
        teamRoom.leaveQueue(requestUserId);
        removeRoomFromRedisQueue(teamRoom.getTeamSize(), teamRoom.getId(), queueToken);
        matchingEventPublisher.publishStatus(teamRoomId, teamRoom.getStatus().name());
        return teamRoom;
    }

    /**
     * ???곹깭 議고쉶
     * - Redis 由ъ뒪??湲곗??쇰줈 ?쒕쾲 / ??? ??怨꾩궛
     * - ?대? 留ㅼ묶 ?꾨즺??寃쎌슦 final room ?뺣낫源뚯? ?ы븿
     */
    @Transactional(readOnly = true)
    public QueueSnapshot getQueueSnapshot(Long teamRoomId, Long requestUserId) {
        GTemporaryTeamRoom teamRoom = getTeamRoomForMemberAction(teamRoomId, requestUserId);

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

        List<Long> queueRoomIds = readQueueRoomIds(teamRoom.getTeamSize(), -1);
        int position = findQueuePosition(queueRoomIds, teamRoomId);
        int totalWaitingTeams = queueRoomIds.size();

        if (position < 0) {
            return new QueueSnapshot(teamRoomId, teamRoom.getStatus().name(), -1, -1, totalWaitingTeams, null, null);
        }

        return new QueueSnapshot(teamRoomId, teamRoom.getStatus().name(), position + 1, position, totalWaitingTeams, null, null);
    }

    /**
     * ??泥섎━
     * - Redis ???쒖꽌瑜?湲곗??쇰줈 ?ㅻ옒 湲곕떎由??遺???묐뒗??
     * - DB ?곹깭媛 ?닿툔??stale entry ??Redis ?먯꽌 ?뺣━?쒕떎.
     * - 泥?踰덉㎏濡??명솚?섎뒗 ? ?섏뼱瑜?諛붾줈 留ㅼ묶 ?꾨즺 泥섎━?쒕떎.
     */
    @Transactional
    public MatchSuccessResult processQueue(GTeamSize teamSize, int scanSize) {
        if (teamSize == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "teamSize???꾩닔?낅땲??");
        }

        String processLockKey = processLockKey(teamSize);
        String lockValue = UUID.randomUUID().toString();

        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(processLockKey, lockValue, MATCH_PROCESS_LOCK_TTL);
        if (!Boolean.TRUE.equals(acquired)) {
            return null;
        }

        try {
            List<Long> orderedRoomIds = readQueueRoomIds(teamSize, scanSize <= 0 ? DEFAULT_MATCH_SCAN_SIZE : scanSize);
            if (orderedRoomIds.size() < 2) {
                return null;
            }

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
        } finally {
            safelyReleaseProcessLock(processLockKey, lockValue);
        }
    }

    /**
     * ??먯씠 ?꾩떆諛⑹뿉???섍컧
     * - 由щ뜑??leave ???cancel ???ъ슜?쒕떎.
     */
    @Transactional
    public GTemporaryTeamRoom leaveTeamRoom(Long teamRoomId, Long userId) {
        GTemporaryTeamRoom teamRoom = temporaryTeamRoomRepository.findByIdForUpdate(teamRoomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REQUEST, "?꾩떆 ?諛⑹쓣 李얠쓣 ???놁뒿?덈떎."));

        GTemporaryTeamMember member = temporaryTeamMemberRepository.findByTeamRoomIdAndUserId(teamRoomId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REQUEST, "?꾩떆 ?諛?硫ㅻ쾭瑜?李얠쓣 ???놁뒿?덈떎."));

        if (!member.isActiveMember()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "?대? ?댁옣????먯엯?덈떎.");
        }
        if (member.isLeader()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "諛⑹옣? ? ?섍?湲곌? ?꾨땲??? ?댁궛???ъ슜?댁빞 ?⑸땲??");
        }

        User user = findUserOrThrow(userId);

        chatService.publishExitMessage(
                teamRoom.getTempChatRoomId(),
                userId,
                user.getNickname() + "?섏씠 ??먯꽌 ?섍컮?듬땲??"
        );

        removeChatRoomMembership(teamRoom.getTempChatRoomId(), userId);
        member.markLeft();
        teamReadyStateRepository.findByTeamRoomIdAndUserId(teamRoomId, userId)
                .ifPresent(teamReadyStateRepository::delete);

        teamRoom.removeMember();
        resetReadyStatesForActiveMembers(teamRoomId);
        return teamRoom;
    }

    /**
     * 諛⑹옣???섑븳 ? ?댁궛
     */
    @Transactional
    public GTemporaryTeamRoom cancelTeamRoom(Long teamRoomId, Long requestUserId) {
        GTemporaryTeamRoom teamRoom = temporaryTeamRoomRepository.findByIdForUpdate(teamRoomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REQUEST, "?꾩떆 ?諛⑹쓣 李얠쓣 ???놁뒿?덈떎."));

        validateActiveMembership(teamRoomId, requestUserId);

        User leader = findUserOrThrow(requestUserId);
        String queueToken = teamRoom.getQueueToken();

        chatService.publishExitMessage(
                teamRoom.getTempChatRoomId(),
                requestUserId,
                leader.getNickname() + "?섏씠 ????댁궛?덉뒿?덈떎."
        );

        teamRoom.cancel(requestUserId);

        List<GTemporaryTeamMember> activeMembers = temporaryTeamMemberRepository
                .findByTeamRoomIdAndLeftAtIsNullOrderByJoinedAtAsc(teamRoomId);
        markMembersLeft(activeMembers);
        removeChatRoomMemberships(teamRoom.getTempChatRoomId(), extractUserIds(activeMembers));
        teamReadyStateRepository.deleteByTeamRoomId(teamRoomId);
        removeRoomFromRedisQueue(teamRoom.getTeamSize(), teamRoomId, queueToken);

        return teamRoom;
    }

    /**
     * ?닿? ?랁븳 ?쒖꽦 ?꾩떆 ?諛?議고쉶
     */
    @Transactional(readOnly = true)
    public Optional<GTemporaryTeamRoom> findMyActiveTeamRoom(Long userId) {
        List<GTemporaryTeamRoom> rooms = temporaryTeamRoomRepository.findActiveRoomsByUserId(userId, ACTIVE_ROOM_STATUSES);
        return rooms.stream().findFirst();
    }

    /**
     * ?뱀젙 ?꾩떆 ?諛⑹쓽 ?쒖꽦 理쒖쥌 洹몃９諛?議고쉶
     */
    @Transactional(readOnly = true)
    public Optional<GFinalGroupChatRoom> findActiveFinalRoom(Long teamRoomId, Long requestUserId) {
        validateActiveMembershipOrClosedRoomAccess(teamRoomId, requestUserId);
        return findLatestFinalRoomByTeamRoomId(teamRoomId);
    }

    private GTemporaryTeamRoom joinTeamRoomInternal(GTemporaryTeamRoom teamRoom, Long userId) {
        User user = findUserOrThrow(userId);
        ensureUserHasNoActiveTeamRoom(userId);

        if (Objects.equals(teamRoom.getLeaderId(), userId)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "諛⑹옣? ?먭린 ????ㅼ떆 ?낆옣?????놁뒿?덈떎.");
        }
        if (teamRoom.getStatus().isTerminal()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "醫낅즺???諛⑹뿉???낆옣?????놁뒿?덈떎.");
        }
        if (!teamRoom.getStatus().canModifyMembers()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "?꾩옱 ?곹깭?먯꽌??????낆옣??遺덇??ν빀?덈떎.");
        }
        if (teamRoom.isFull()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "? ?뺤썝??媛??李쇱뒿?덈떎.");
        }
        validateUserTeamGender(userId, teamRoom.getTeamGender());

        if (temporaryTeamMemberRepository.existsByTeamRoomIdAndUserIdAndLeftAtIsNull(teamRoom.getId(), userId)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "?대? ?대떦 ????쒖꽦 硫ㅻ쾭?낅땲??");
        }

        teamRoom.addMember();
        temporaryTeamMemberRepository.save(GTemporaryTeamMember.create(teamRoom.getId(), userId, false));
        teamReadyStateRepository.save(GTeamReadyState.create(teamRoom.getId(), userId));
        resetReadyStatesForActiveMembers(teamRoom.getId());

        if (teamRoom.isFull()) {
            teamRoom.enterReadyCheck(teamRoom.getLeaderId());
        }

        chatService.addMembersToRoom(teamRoom.getTempChatRoomId(), List.of(userId));
        chatService.publishEnterMessage(
                teamRoom.getTempChatRoomId(),
                userId,
                user.getNickname() + "?섏씠 ????낆옣?덉뒿?덈떎."
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
        matchingPushService.notifyMatched(finalMemberIds, finalGroupChatRoom.getId(), finalChatRoom.getId());

        chatService.publishEnterMessage(
                first.getTempChatRoomId(),
                first.getLeaderId(),
                "留ㅼ묶???꾨즺?섏뿀?듬땲?? 理쒖쥌 洹몃９ 梨꾪똿諛⑹쑝濡??대룞?⑸땲??"
        );
        chatService.publishEnterMessage(
                second.getTempChatRoomId(),
                second.getLeaderId(),
                "留ㅼ묶???꾨즺?섏뿀?듬땲?? 理쒖쥌 洹몃９ 梨꾪똿諛⑹쑝濡??대룞?⑸땲??"
        );
        chatService.publishEnterMessage(
                finalChatRoom.getId(),
                first.getLeaderId(),
                "留ㅼ묶???꾨즺?섏뿀?듬땲?? 理쒖쥌 洹몃９ 梨꾪똿諛⑹씠 ?앹꽦?섏뿀?듬땲??"
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

    private void validateReadyToFinalize(GTemporaryTeamRoom room, List<GTemporaryTeamMember> members) {
        if (room.getStatus() != GTemporaryTeamRoomStatus.QUEUE_WAITING) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "???湲?以묒씤 ?留?留ㅼ묶?????덉뒿?덈떎.");
        }
        if (members.size() != room.getTeamSize().getValue()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "? ?몄썝 ?섍? ?뺤썝怨??쇱튂?섏? ?딆뒿?덈떎.");
        }
    }

    private void validateActiveMembership(Long teamRoomId, Long userId) {
        boolean isActiveMember = temporaryTeamMemberRepository.existsByTeamRoomIdAndUserIdAndLeftAtIsNull(teamRoomId, userId);
        if (!isActiveMember) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "?대떦 ?꾩떆 ?諛⑹쓽 ?쒖꽦 硫ㅻ쾭媛 ?꾨떃?덈떎.");
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
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN, "?대떦 ?꾩떆 ?諛??묎렐 沅뚰븳???놁뒿?덈떎."));

        if (member.getLeftAt() == null) {
            return;
        }

        GTemporaryTeamRoom room = temporaryTeamRoomRepository.findById(teamRoomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REQUEST, "?꾩떆 ?諛⑹쓣 李얠쓣 ???놁뒿?덈떎."));

        if (room.getStatus() != GTemporaryTeamRoomStatus.CLOSED && room.getStatus() != GTemporaryTeamRoomStatus.MATCHED) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "?대떦 ?꾩떆 ?諛??묎렐 沅뚰븳???놁뒿?덈떎.");
        }
    }

    private GTemporaryTeamRoom getTeamRoomForMemberAction(Long teamRoomId, Long userId) {
        GTemporaryTeamRoom teamRoom = temporaryTeamRoomRepository.findByIdForUpdate(teamRoomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REQUEST, "?꾩떆 ?諛⑹쓣 李얠쓣 ???놁뒿?덈떎."));
        validateActiveMembership(teamRoomId, userId);
        return teamRoom;
    }

    private void ensureUserHasNoActiveTeamRoom(Long userId) {
        List<GTemporaryTeamRoom> activeRooms = temporaryTeamRoomRepository.findActiveRoomsByUserId(userId, ACTIVE_ROOM_STATUSES);
        if (!activeRooms.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "?대? 李몄뿬 以묒씤 ?꾩떆 ?諛⑹씠 ?덉뒿?덈떎.");
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
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "팀 성별 정보가 없습니다.");
        }

        UserProfile profile = userProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REQUEST, "프로필 성별을 먼저 설정해 주세요."));

        if (profile.getGender() == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "프로필 성별을 먼저 설정해 주세요.");
        }

        GTeamGender userTeamGender = mapUserGenderToTeamGender(profile.getGender());
        if (userTeamGender != teamGender) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "팀 성별과 사용자 성별이 일치하지 않습니다.");
        }
    }

    private GTeamGender mapUserGenderToTeamGender(Gender gender) {
        return gender == Gender.M ? GTeamGender.M : GTeamGender.F;
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

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
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
                log.warn("?섎せ????媛?諛쒓껄. value={}", value);
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
}



