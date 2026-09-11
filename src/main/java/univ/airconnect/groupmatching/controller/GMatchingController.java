package univ.airconnect.groupmatching.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import univ.airconnect.global.error.BusinessException;
import univ.airconnect.global.error.ErrorCode;
import univ.airconnect.groupmatching.domain.GTemporaryTeamRoomStatus;
import univ.airconnect.groupmatching.domain.entity.GTemporaryTeamMember;
import univ.airconnect.groupmatching.domain.entity.GTemporaryTeamRoom;
import univ.airconnect.groupmatching.dto.request.GMatchingRequest;
import univ.airconnect.groupmatching.dto.response.GMatchingResponse;
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
    private final GTemporaryTeamMemberRepository temporaryTeamMemberRepository;
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
                request.getTeamSize()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(toRoomResponse(teamRoom, userId));
    }

    /**
     * 초대 코드로 임시방에 입장한다.
     */
    @PostMapping("/join-by-invite")
    public ResponseEntity<GMatchingResponse.TemporaryTeamRoomResponse> joinRoomByInviteCode(
            @Valid @RequestBody GMatchingRequest.JoinByInviteCodeRequest request,
            Authentication authentication
    ) {
        Long userId = currentUserId(authentication);
        GTemporaryTeamRoom teamRoom = matchingService.joinRoomByInviteCode(request.getInviteCode(), userId);
        return ResponseEntity.ok(toRoomResponse(teamRoom, userId));
    }

    /**
     * 방장이 초대 코드를 재발급한다.
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
                    GMatchingResponse.QueueSnapshotResponse queueSnapshotResponse = null;

                    if (room.getStatus() == GTemporaryTeamRoomStatus.QUEUE_WAITING) {
                        GMatchingService.QueueSnapshot snapshot = matchingService.getQueueSnapshot(room.getId(), userId);
                        queueSnapshotResponse = GMatchingResponse.QueueSnapshotResponse.from(snapshot);
                    }

                    return ResponseEntity.ok(
                            GMatchingResponse.MyMatchingStateResponse.inTemporaryTeamRoom(
                                    toRoomResponse(room, userId),
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
                        .orElseGet(() -> ResponseEntity.ok(
                                GMatchingResponse.MyMatchingStateResponse.idle()
                        )));
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
    public ResponseEntity<Void> leaveTeamRoom(
            @PathVariable Long teamRoomId,
            Authentication authentication
    ) {
        Long userId = currentUserId(authentication);
        matchingService.leaveTeamRoom(teamRoomId, userId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{teamRoomId}/members/{targetUserId}")
    public ResponseEntity<GMatchingResponse.TemporaryTeamRoomResponse> expelTeamMember(
            @PathVariable Long teamRoomId,
            @PathVariable Long targetUserId,
            Authentication authentication
    ) {
        Long userId = currentUserId(authentication);
        GTemporaryTeamRoom teamRoom = matchingService.expelTeamMember(teamRoomId, userId, targetUserId);
        return ResponseEntity.ok(toRoomResponse(teamRoom, userId));
    }

    /**
     * 방장이 임시 팀방을 해산한다.
     */
    @DeleteMapping("/{teamRoomId}")
    public ResponseEntity<Void> cancelTeamRoom(
            @PathVariable Long teamRoomId,
            Authentication authentication
    ) {
        Long userId = currentUserId(authentication);
        matchingService.cancelTeamRoom(teamRoomId, userId);
        return ResponseEntity.noContent().build();
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
        List<GTemporaryTeamMember> members = temporaryTeamMemberRepository
                .findByTeamRoomIdAndLeftAtIsNullOrderByJoinedAtAsc(room.getId());
        List<Long> userIds = members.stream().map(GTemporaryTeamMember::getUserId).toList();
        Map<Long, User> userMap = userRepository.findAllByIdWithProfile(userIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user));
        int requiredTickets = room.getTeamSize().getValue();
        List<GMatchingResponse.TeamMemberSummaryResponse> memberResponses = members.stream()
                .map(member -> {
                    User user = userMap.get(member.getUserId());
                    return new GMatchingResponse.TeamMemberSummaryResponse(member.getUserId(),
                            user != null ? user.getNickname() : null, extractProfileImage(user),
                            member.isLeader(), member.getJoinedAt(),
                            user != null && user.getTickets() >= requiredTickets);
                }).toList();
        boolean meLeader = room.isLeader(currentUserId);
        boolean allHaveTickets = members.size() == requiredTickets
                && memberResponses.stream().allMatch(GMatchingResponse.TeamMemberSummaryResponse::hasEnoughTickets);
        boolean active = members.stream().anyMatch(m -> Objects.equals(m.getUserId(), currentUserId));
        boolean editable = room.getStatus().canModifyMembers();
        boolean waiting = room.getStatus() == GTemporaryTeamRoomStatus.QUEUE_WAITING;
        User me = userMap.get(currentUserId);
        return new GMatchingResponse.TemporaryTeamRoomResponse(
                room.getId(), room.getLeaderId(), meLeader, room.getTeamGender(), room.getTeamSize(),
                requiredTickets, members.size(), members.size() == requiredTickets, room.getDisplayStatus(),
                room.getInviteCode(), "airconnect://matching/join?inviteCode=" + room.getInviteCode(),
                active && meLeader && editable && room.isFull() && allHaveTickets,
                active && waiting, active && !meLeader && editable,
                active && meLeader && (editable || waiting),
                requiredTickets, me != null ? me.getTickets() : 0, allHaveTickets,
                room.getQueuedAt(), room.getCreatedAt(), room.getUpdatedAt(), memberResponses);
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

            throw new BusinessException(ErrorCode.UNAUTHORIZED, "인증 정보에서 사용자 ID를 추출할 수 없습니다.");
    }
}



