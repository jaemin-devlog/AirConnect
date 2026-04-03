package univ.airconnect.groupmatching.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import univ.airconnect.chat.dto.request.SendMessageRequest;
import univ.airconnect.chat.dto.response.ChatMessageResponse;
import univ.airconnect.chat.service.ChatService;
import univ.airconnect.global.error.BusinessException;
import univ.airconnect.global.error.ErrorCode;
import univ.airconnect.groupmatching.domain.GTemporaryTeamRoomStatus;
import univ.airconnect.groupmatching.domain.entity.GTeamReadyState;
import univ.airconnect.groupmatching.domain.entity.GTemporaryTeamMember;
import univ.airconnect.groupmatching.domain.entity.GTemporaryTeamRoom;
import univ.airconnect.groupmatching.dto.request.GMatchingRequest;
import univ.airconnect.groupmatching.dto.response.GMatchingResponse;
import univ.airconnect.groupmatching.repository.GTeamReadyStateRepository;
import univ.airconnect.groupmatching.repository.GTemporaryTeamMemberRepository;
import univ.airconnect.groupmatching.service.GMatchingService;
import univ.airconnect.matching.dto.response.MatchingCandidateResponse;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.repository.UserRepository;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/matching/team-rooms")
@RequiredArgsConstructor
@Validated
public class GMatchingController {

    private static final String MATCHING_TEAM_ROOM_SUB_PREFIX = "/sub/matching/team-room/";

    private final GMatchingService matchingService;
    private final ChatService chatService;
    private final GTemporaryTeamMemberRepository temporaryTeamMemberRepository;
    private final GTeamReadyStateRepository teamReadyStateRepository;
    private final UserRepository userRepository;

    /**
     * 1단계. 임시 팀방을 생성한다.
     */
    @PostMapping
    public ResponseEntity<GMatchingResponse.TemporaryTeamRoomResponse> createTemporaryTeamRoom(
            @Valid @RequestBody GMatchingRequest.CreateTemporaryTeamRoomRequest request,
            Authentication authentication
    ) {
        Long userId = currentUserId(authentication);

        GTemporaryTeamRoom teamRoom = matchingService.createTemporaryTeamRoom(
                userId,
                request.getTeamName(),
                request.getTeamGender(),
                request.getTeamSize(),
                request.getOpponentGenderFilter(),
                request.getVisibility()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(toRoomResponse(teamRoom, userId));
    }

    /**
     * 공개 모집 중인 임시 팀방 목록을 조회한다.
     * 현재는 teamSize 기준으로만 직접 필터링한다.
     */
    @GetMapping({"/public", "/recruitable"})
    public ResponseEntity<GMatchingResponse.RecruitableTeamRoomPageResponse> getRecruitableTeamRooms(
            @RequestParam("teamSize") univ.airconnect.groupmatching.domain.GTeamSize teamSize,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ResponseEntity.ok(matchingService.findRecruitableTeamRooms(teamSize, page, size));
    }

    /**
     * 공개방에 입장한다.
     */
    @PostMapping("/{teamRoomId}/join")
    public ResponseEntity<GMatchingResponse.TemporaryTeamRoomResponse> joinPublicRoom(
            @PathVariable Long teamRoomId,
            Authentication authentication
    ) {
        Long userId = currentUserId(authentication);
        GTemporaryTeamRoom teamRoom = matchingService.joinPublicRoom(teamRoomId, userId);
        return ResponseEntity.ok(toRoomResponse(teamRoom, userId));
    }

    /**
     * 초대 코드로 임시방에 입장한다.
     */
    @PostMapping("/join-by-invite")
    public ResponseEntity<GMatchingResponse.TemporaryTeamRoomResponse> joinRoomByInviteCode(
            @Valid @RequestBody GMatchingRequest.JoinPrivateRoomRequest request,
            Authentication authentication
    ) {
        Long userId = currentUserId(authentication);
        GTemporaryTeamRoom teamRoom = matchingService.joinRoomByInviteCode(request.getInviteCode(), userId);
        return ResponseEntity.ok(toRoomResponse(teamRoom, userId));
    }

    /**
     * 공개방과 비공개방 모두 초대 코드를 생성할 수 있다.
     */
    @PostMapping("/{teamRoomId}/invite-code")
    public ResponseEntity<GMatchingResponse.TemporaryTeamRoomResponse> generateInviteCode(
            @PathVariable Long teamRoomId,
            Authentication authentication
    ) {
        Long userId = currentUserId(authentication);
        GTemporaryTeamRoom teamRoom = matchingService.generateInviteCode(teamRoomId, userId);
        return ResponseEntity.ok(toRoomResponse(teamRoom, userId));
    }

    @GetMapping("/{teamRoomId}/members/{targetUserId}/profile")
    public ResponseEntity<MatchingCandidateResponse> getTeamMemberProfile(
            @PathVariable Long teamRoomId,
            @PathVariable Long targetUserId,
            Authentication authentication
    ) {
        Long userId = currentUserId(authentication);
        return ResponseEntity.ok(matchingService.getTeamMemberProfile(teamRoomId, userId, targetUserId));
    }

    /**
     * 임시방 내부 채팅 메시지를 조회한다.
     */
    @GetMapping({"/{teamRoomId}/chat/messages", "/{teamRoomId}/chat/messages/"})
    public ResponseEntity<List<ChatMessageResponse>> getTeamRoomMessages(
            @PathVariable Long teamRoomId,
            @RequestParam(required = false) @Positive Long lastMessageId,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            Authentication authentication
    ) {
        Long userId = currentUserId(authentication);
        Long chatRoomId = matchingService.getTempChatRoomId(teamRoomId, userId);
        return ResponseEntity.ok(chatService.findMessagesByRoomId(chatRoomId, userId, lastMessageId, size));
    }

    /**
     * 임시방 내부 채팅 메시지를 전송한다.
     */
    @PostMapping({"/{teamRoomId}/chat/messages", "/{teamRoomId}/chat/messages/"})
    public ResponseEntity<ChatMessageResponse> sendTeamRoomMessage(
            @PathVariable Long teamRoomId,
            @Valid @RequestBody SendMessageRequest request,
            Authentication authentication
    ) {
        Long userId = currentUserId(authentication);
        Long chatRoomId = matchingService.getTempChatRoomId(teamRoomId, userId);
        return ResponseEntity.ok(chatService.sendMessage(userId, chatRoomId, request));
    }

    /**
     * 임시방 내부 채팅 읽음 상태를 갱신한다.
     */
    @PatchMapping({"/{teamRoomId}/chat/read", "/{teamRoomId}/chat/read/"})
    public ResponseEntity<Void> updateTeamRoomChatRead(
            @PathVariable Long teamRoomId,
            Authentication authentication
    ) {
        Long userId = currentUserId(authentication);
        Long chatRoomId = matchingService.getTempChatRoomId(teamRoomId, userId);
        chatService.updateLastRead(chatRoomId, userId);
        return ResponseEntity.ok().build();
    }

    /**
     * 내가 현재 참여 중인 활성 임시 팀방을 조회한다.
     */
    @GetMapping({"/me", "/me/"})
    public ResponseEntity<GMatchingResponse.TemporaryTeamRoomResponse> getMyActiveTeamRoom(
            Authentication authentication
    ) {
        Long userId = currentUserId(authentication);

        return matchingService.findMyActiveTeamRoom(userId)
                .map(room -> ResponseEntity.ok(toRoomResponse(room, userId)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /**
     * 모바일 앱이 재실행되었을 때 현재 과팅 상태를 한 번에 복구한다.
     * - 임시 팀방에 있으면 팀방 정보와 큐 상태, 구독 경로를 내려준다.
     * - 최종 그룹방에 있으면 최종 그룹방 정보를 내려준다.
     * - 아무 상태가 없으면 IDLE을 내려준다.
     */
    @GetMapping({"/me/state", "/me/state/"})
    public ResponseEntity<GMatchingResponse.MyMatchingStateResponse> getMyMatchingState(
            Authentication authentication
    ) {
        Long userId = currentUserId(authentication);

        return matchingService.findMyActiveTeamRoom(userId)
                .map(room -> {
                    GMatchingResponse.TemporaryTeamRoomResponse teamRoomResponse = toRoomResponse(room, userId);
                    GMatchingResponse.QueueSnapshotResponse queueSnapshotResponse = null;

                    if (room.getStatus() == GTemporaryTeamRoomStatus.QUEUE_WAITING) {
                        GMatchingService.QueueSnapshot snapshot = matchingService.getQueueSnapshot(room.getId(), userId);
                        queueSnapshotResponse = GMatchingResponse.QueueSnapshotResponse.from(snapshot);
                    }

                    return ResponseEntity.ok(
                            GMatchingResponse.MyMatchingStateResponse.inTemporaryTeamRoom(
                                    teamRoomResponse,
                                    queueSnapshotResponse,
                                    matchingSubscriptionDestination(room.getId())
                            )
                    );
                })
                .orElseGet(() -> matchingService.findMyActiveFinalRoom(userId)
                        .map(finalRoom -> ResponseEntity.ok(
                                GMatchingResponse.MyMatchingStateResponse.inFinalGroupRoom(
                                        GMatchingResponse.FinalGroupChatRoomResponse.from(finalRoom)
                                )
                        ))
                        .orElseGet(() -> ResponseEntity.ok(GMatchingResponse.MyMatchingStateResponse.idle())));
    }

    /**
     * 팀원 준비 상태를 변경한다.
     */
    @PatchMapping("/{teamRoomId}/ready")
    public ResponseEntity<GMatchingResponse.TemporaryTeamRoomResponse> updateReadyState(
            @PathVariable Long teamRoomId,
            @Valid @RequestBody GMatchingRequest.UpdateReadyStateRequest request,
            Authentication authentication
    ) {
        Long userId = currentUserId(authentication);

        matchingService.updateReadyState(teamRoomId, userId, request.getReady());

        GTemporaryTeamRoom room = matchingService.findMyActiveTeamRoom(userId)
                .filter(r -> Objects.equals(r.getId(), teamRoomId))
                .orElseThrow(() -> new BusinessException(ErrorCode.TEAM_ROOM_NOT_FOUND));

        return ResponseEntity.ok(toRoomResponse(room, userId));
    }

    /**
     * 방장이 매칭을 시작하면 Redis 큐에 등록하고 즉시 매칭을 시도한다.
     */
    @PostMapping("/{teamRoomId}/queue/start")
    public ResponseEntity<GMatchingResponse.QueueSnapshotResponse> startMatching(
            @PathVariable Long teamRoomId,
            Authentication authentication
    ) {
        Long userId = currentUserId(authentication);
        GMatchingService.QueueSnapshot snapshot = matchingService.startMatching(teamRoomId, userId);
        return ResponseEntity.ok(GMatchingResponse.QueueSnapshotResponse.from(snapshot));
    }

    /**
     * 매칭 큐 상태를 조회한다.
     */
    @GetMapping("/{teamRoomId}/queue")
    public ResponseEntity<GMatchingResponse.QueueSnapshotResponse> getQueueSnapshot(
            @PathVariable Long teamRoomId,
            Authentication authentication
    ) {
        Long userId = currentUserId(authentication);
        GMatchingService.QueueSnapshot snapshot = matchingService.getQueueSnapshot(teamRoomId, userId);
        return ResponseEntity.ok(GMatchingResponse.QueueSnapshotResponse.from(snapshot));
    }

    /**
     * 매칭 큐에서 이탈한다.
     */
    @PostMapping("/{teamRoomId}/queue/leave")
    public ResponseEntity<GMatchingResponse.TemporaryTeamRoomResponse> leaveMatchingQueue(
            @PathVariable Long teamRoomId,
            Authentication authentication
    ) {
        Long userId = currentUserId(authentication);
        GTemporaryTeamRoom teamRoom = matchingService.leaveMatchingQueue(teamRoomId, userId);
        return ResponseEntity.ok(toRoomResponse(teamRoom, userId));
    }

    /**
     * 팀원이 임시 팀방에서 나간다.
     */
    @PostMapping("/{teamRoomId}/leave")
    public ResponseEntity<GMatchingResponse.TemporaryTeamRoomResponse> leaveTeamRoom(
            @PathVariable Long teamRoomId,
            Authentication authentication
    ) {
        Long userId = currentUserId(authentication);
        GTemporaryTeamRoom teamRoom = matchingService.leaveTeamRoom(teamRoomId, userId);
        return ResponseEntity.ok(toRoomResponse(teamRoom, userId));
    }

    /**
     * 방장이 임시 팀방을 해산한다.
     */
    @DeleteMapping("/{teamRoomId}")
    public ResponseEntity<GMatchingResponse.TemporaryTeamRoomResponse> cancelTeamRoom(
            @PathVariable Long teamRoomId,
            Authentication authentication
    ) {
        Long userId = currentUserId(authentication);
        GTemporaryTeamRoom teamRoom = matchingService.cancelTeamRoom(teamRoomId, userId);
        return ResponseEntity.ok(toRoomResponse(teamRoom, userId));
    }

    /**
     * 최종 그룹 채팅방을 조회한다.
     */
    @GetMapping("/{teamRoomId}/final-room")
    public ResponseEntity<GMatchingResponse.FinalGroupChatRoomResponse> getActiveFinalRoom(
            @PathVariable Long teamRoomId,
            Authentication authentication
    ) {
        Long userId = currentUserId(authentication);

        return matchingService.findActiveFinalRoom(teamRoomId, userId)
                .map(GMatchingResponse.FinalGroupChatRoomResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    private GMatchingResponse.TemporaryTeamRoomResponse toRoomResponse(GTemporaryTeamRoom room, Long currentUserId) {
        List<GTemporaryTeamMember> members = temporaryTeamMemberRepository.findByTeamRoomIdOrderByJoinedAtAsc(room.getId());

        Map<Long, Boolean> readyMap = teamReadyStateRepository.findByTeamRoomIdOrderByIdAsc(room.getId())
                .stream()
                .collect(Collectors.toMap(
                        GTeamReadyState::getUserId,
                        GTeamReadyState::isReady,
                        (left, right) -> right
                ));

        List<Long> userIds = members.stream()
                .map(GTemporaryTeamMember::getUserId)
                .distinct()
                .toList();

        Map<Long, User> userMap = userRepository.findAllByIdWithProfile(userIds)
                .stream()
                .collect(Collectors.toMap(
                        User::getId,
                        user -> user,
                        (left, right) -> left
                ));

        List<GMatchingResponse.TeamMemberSummaryResponse> memberResponses = members.stream()
                .map(member -> GMatchingResponse.TeamMemberSummaryResponse.from(
                        member,
                        userMap.containsKey(member.getUserId()) ? userMap.get(member.getUserId()).getNickname() : null,
                        extractProfileImage(userMap.get(member.getUserId())),
                        Boolean.TRUE.equals(readyMap.get(member.getUserId()))
                ))
                .toList();

        int readyMemberCount = (int) members.stream()
                .filter(GTemporaryTeamMember::isActiveMember)
                .filter(member -> Boolean.TRUE.equals(readyMap.get(member.getUserId())))
                .count();

        boolean allMembersReady = room.getCurrentMemberCount() == room.getTeamSize().getValue()
                && readyMemberCount == room.getTeamSize().getValue();

        boolean meLeader = Objects.equals(room.getLeaderId(), currentUserId);

        boolean canStartMatching = meLeader
                && room.getStatus() == GTemporaryTeamRoomStatus.READY_CHECK
                && allMembersReady;

        return GMatchingResponse.TemporaryTeamRoomResponse.of(
                room,
                meLeader,
                readyMemberCount,
                allMembersReady,
                canStartMatching,
                memberResponses
        );
    }

    private String matchingSubscriptionDestination(Long teamRoomId) {
        return MATCHING_TEAM_ROOM_SUB_PREFIX + teamRoomId;
    }

    private String extractProfileImage(User user) {
        if (user == null || user.getUserProfile() == null) {
            return null;
        }
        return user.getUserProfile().getProfileImagePath();
    }

    /**
     * principal 구현이 고정되어 있지 않아
     * 1) principal.getId()
     * 2) authentication.getName()
     * 순서로 userId를 최대한 유연하게 추출한다.
     */
    private Long currentUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "인증 정보가 필요합니다.");
        }

        Object principal = authentication.getPrincipal();
        if (principal != null) {
            try {
                Method method = principal.getClass().getMethod("getId");
                Object value = method.invoke(principal);
                if (value != null) {
                    return Long.valueOf(String.valueOf(value));
                }
            } catch (Exception ignored) {
            }
        }

        String name = authentication.getName();
        if (name != null && !name.isBlank() && !"anonymousUser".equals(name)) {
            try {
                return Long.valueOf(name);
            } catch (NumberFormatException ignored) {
            }
        }

        throw new BusinessException(ErrorCode.UNAUTHORIZED, "인증 principal에서 userId를 추출할 수 없습니다.");
    }
}



