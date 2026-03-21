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
import univ.airconnect.groupmatching.domain.entity.GTeamReadyState;
import univ.airconnect.groupmatching.domain.entity.GTemporaryTeamMember;
import univ.airconnect.groupmatching.domain.entity.GTemporaryTeamRoom;
import univ.airconnect.groupmatching.dto.request.GMatchingRequest;
import univ.airconnect.groupmatching.dto.response.GMatchingResponse;
import univ.airconnect.groupmatching.repository.GTeamReadyStateRepository;
import univ.airconnect.groupmatching.repository.GTemporaryTeamMemberRepository;
import univ.airconnect.groupmatching.service.GMatchingService;
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

    private final GMatchingService matchingService;
    private final GTemporaryTeamMemberRepository temporaryTeamMemberRepository;
    private final GTeamReadyStateRepository teamReadyStateRepository;
    private final UserRepository userRepository;

    /**
     * Step 1. 임시 팀방 생성
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
                request.getVisibility(),
                request.getAgeRangeMin(),
                request.getAgeRangeMax()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(toRoomResponse(teamRoom, userId));
    }

    /**
     * 공개 모집 중인 임시방 목록 조회
     * 현재 service/repository 기준으로 teamSize 필터만 직접 지원
     */
    @GetMapping("/public")
    public ResponseEntity<List<GMatchingResponse.PublicTeamRoomSummaryResponse>> getRecruitablePublicRooms(
            @RequestParam("teamSize") univ.airconnect.groupmatching.domain.GTeamSize teamSize
    ) {
        List<GMatchingResponse.PublicTeamRoomSummaryResponse> response = matchingService.findRecruitablePublicRooms(teamSize)
                .stream()
                .map(GMatchingResponse.PublicTeamRoomSummaryResponse::from)
                .toList();

        return ResponseEntity.ok(response);
    }

    /**
     * 공개방 입장
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
     * 비공개방 초대코드 입장
     */
    @PostMapping("/join-by-invite")
    public ResponseEntity<GMatchingResponse.TemporaryTeamRoomResponse> joinPrivateRoomByInviteCode(
            @Valid @RequestBody GMatchingRequest.JoinPrivateRoomRequest request,
            Authentication authentication
    ) {
        Long userId = currentUserId(authentication);
        GTemporaryTeamRoom teamRoom = matchingService.joinPrivateRoomByInviteCode(request.getInviteCode(), userId);
        return ResponseEntity.ok(toRoomResponse(teamRoom, userId));
    }

    /**
     * 내가 현재 참여 중인 활성 임시 팀방 조회
     */
    @GetMapping("/me")
    public ResponseEntity<GMatchingResponse.TemporaryTeamRoomResponse> getMyActiveTeamRoom(
            Authentication authentication
    ) {
        Long userId = currentUserId(authentication);

        return matchingService.findMyActiveTeamRoom(userId)
                .map(room -> ResponseEntity.ok(toRoomResponse(room, userId)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /**
     * 팀원 준비 상태 변경
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
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REQUEST, "임시 팀방을 찾을 수 없습니다."));

        return ResponseEntity.ok(toRoomResponse(room, userId));
    }

    /**
     * 방장이 매칭 시작 -> Redis 큐 등록 + 즉시 매칭 시도
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
     * 큐 상태 조회
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
     * 큐 이탈
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
     * 팀원이 임시방에서 나감
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
     * 방장이 팀 해산
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
     * 최종 그룹 채팅방 조회
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

        Map<Long, String> nicknameMap = userRepository.findAllById(userIds)
                .stream()
                .collect(Collectors.toMap(
                        User::getId,
                        User::getNickname,
                        (left, right) -> left
                ));

        List<GMatchingResponse.TeamMemberSummaryResponse> memberResponses = members.stream()
                .map(member -> GMatchingResponse.TeamMemberSummaryResponse.from(
                        member,
                        nicknameMap.get(member.getUserId()),
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

    /**
     * 현재 프로젝트의 principal 구현을 모르므로
     * 1) principal.getId()
     * 2) authentication.getName()
     * 순서로 최대한 유연하게 userId를 추출
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