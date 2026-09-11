package univ.airconnect.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import univ.airconnect.chat.domain.ChatRoomType;
import univ.airconnect.chat.repository.ChatRoomRepository;
import univ.airconnect.chat.repository.ChatRoomMemberRepository;
import univ.airconnect.global.error.BusinessException;
import univ.airconnect.global.error.ErrorCode;
import univ.airconnect.groupmatching.domain.*;
import univ.airconnect.groupmatching.domain.entity.*;
import univ.airconnect.groupmatching.repository.*;
import univ.airconnect.user.domain.UserRole;
import univ.airconnect.user.domain.UserStatus;
import univ.airconnect.user.repository.UserRepository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

import static univ.airconnect.admin.AdminGroupMatchingDtos.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminGroupMatchingService {
    private final AdminGroupMatchingRepository diagnostics;
    private final GTemporaryTeamRoomRepository teams;
    private final GTemporaryTeamMemberRepository members;
    private final GTeamReadyStateRepository readiness;
    private final UserRepository users;
    private final ChatRoomRepository chatRooms;
    private final ChatRoomMemberRepository chatMembers;
    private final AdminGroupQueueObserver queueObserver;

    public AdminDtos.PageResponse<Team> list(Long adminId, Long userId, Long teamId,
                                             GTemporaryTeamRoomStatus status, int page, int size) {
        requireAdmin(adminId);
        if (page < 0 || size < 1 || size > 50 || (userId != null && userId <= 0)
                || (teamId != null && teamId <= 0)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "회원·팀 번호와 조회 범위를 확인하세요.");
        }
        return AdminDtos.PageResponse.from(diagnostics.search(userId, teamId, status, PageRequest.of(page, size))
                .map(this::summary));
    }

    public Detail detail(Long adminId, Long teamId) {
        requireAdmin(adminId);
        if (teamId == null || teamId <= 0) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        GTemporaryTeamRoom team = teams.findById(teamId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "팀 기록을 찾을 수 없습니다."));
        List<String> observations = new ArrayList<>();
        var memberRows = members.findByTeamRoomIdOrderByJoinedAtAsc(teamId);
        var readyRows = readiness.findByTeamRoomIdOrderByIdAsc(teamId);
        var readyByUser = readyRows.stream().collect(Collectors.toMap(GTeamReadyState::getUserId, r -> r));
        var userById = users.findAllById(memberRows.stream().map(GTemporaryTeamMember::getUserId).toList())
                .stream().collect(Collectors.toMap(u -> u.getId(), u -> u));
        List<Participant> participants = memberRows.stream().map(m -> {
            var user = userById.get(m.getUserId());
            var ready = readyByUser.get(m.getUserId());
            return new Participant(m.getUserId(), user == null ? null : user.getNickname(), user != null,
                    m.isLeader(), m.isActiveMember(), ready == null ? null : ready.isReady(),
                    m.getJoinedAt(), m.getLeftAt(), m.getExpelledAt());
        }).toList();
        int activeCount = (int) participants.stream().filter(Participant::active).count();
        int readyCount = (int) participants.stream().filter(p -> p.active() && Boolean.TRUE.equals(p.ready())).count();
        if (!team.getStatus().isTerminal()) {
            if (activeCount != team.getCurrentMemberCount()) observations.add("저장 인원과 현재 임시팀 참여 기록 수가 다릅니다.");
        }
        if (participants.stream().anyMatch(p -> !p.userRecordPresent())) observations.add("회원 정보가 없어진 참여 기록이 있습니다.");

        var resultRows = diagnostics.results(teamId, PageRequest.of(0, 21));
        var finalRows = diagnostics.finalRooms(teamId, PageRequest.of(0, 21));
        boolean truncated = resultRows.size() > 20 || finalRows.size() > 20;
        if (truncated) observations.add("연결 기록은 최근 20건씩만 조회했습니다. 범위 밖 기록은 판단할 수 없습니다.");
        resultRows = resultRows.stream().limit(20).toList();
        finalRows = finalRows.stream().limit(20).toList();
        List<Match> matches = resultRows.stream().map(r -> {
            Long opponentId = r.getOpponentTeamRoomId(teamId);
            var opponent = teams.findById(opponentId);
            return new Match(r.getId(), r.getStatus().name(), opponentId,
                    opponent.map(GTemporaryTeamRoom::getTeamName).orElse(null), opponent.isPresent(),
                    opponent.map(o -> o.getStatus().name()).orElse(null),
                    opponent.isPresent() ? members.countByTeamRoomIdAndLeftAtIsNull(opponentId) : null,
                    r.getFinalGroupChatRoomId(), r.getMatchedAt(), r.getFinalRoomCreatedAt());
        }).toList();
        List<FinalRoom> finals = finalRows.stream().map(f -> new FinalRoom(f.getId(), f.getMatchResultId(),
                f.getTeam1RoomId(), f.getTeam2RoomId(), f.getTeamSize().getValue(), f.getStatus().name(),
                room(f.getChatRoomId()), f.getCreatedAt())).toList();
        Room temporary = room(team.getTempChatRoomId());
        if (team.getTempChatRoomId() != null && (!temporary.exists() || !temporary.group()))
            observations.add("구버전 임시 채팅방 연결을 확인할 수 없습니다.");
        if (matches.stream().anyMatch(m -> !m.opponentExists())) observations.add("상대 팀 기록 일부를 찾을 수 없습니다.");
        boolean linksValid = true;
        for (var result : resultRows) {
            var linked = finalRows.stream().filter(f -> f.getMatchResultId().equals(result.getId())).toList();
            if (result.getStatus() == GMatchResultStatus.FINAL_ROOM_CREATED) {
                boolean valid = linked.size() == 1
                        && Objects.equals(linked.get(0).getId(), result.getFinalGroupChatRoomId())
                        && Objects.equals(linked.get(0).getTeam1RoomId(), result.getTeam1RoomId())
                        && Objects.equals(linked.get(0).getTeam2RoomId(), result.getTeam2RoomId())
                        && linked.get(0).getTeamSize() == team.getTeamSize()
                        && finals.stream().anyMatch(f -> f.resultId().equals(result.getId()) && f.chatRoom().exists() && f.chatRoom().group());
                if (!valid) linksValid = false;
            } else if (!linked.isEmpty() || result.getFinalGroupChatRoomId() != null) linksValid = false;
        }
        for (var f : finalRows) {
            if (resultRows.stream().noneMatch(r -> r.getId().equals(f.getMatchResultId()))) linksValid = false;
        }
        if (!linksValid) observations.add("매칭 결과·최종방·실제 채팅방의 연결이 누락되었거나 서로 다릅니다.");
        var currentResults = resultRows.stream().filter(r -> r.getStatus() != GMatchResultStatus.CANCELLED).toList();
        String stage = switch (team.getStatus()) {
            case OPEN, READY_CHECK -> activeCount == team.getTeamSize().getValue()
                    ? "WAITING_FOR_START" : "RECRUITING";
            case QUEUE_WAITING -> "WAITING";
            case CANCELLED -> "CANCELLED";
            case MATCHED -> currentResults.size() == 1 && currentResults.get(0).getStatus() == GMatchResultStatus.MATCHED
                    && finals.isEmpty() ? "FINAL_ROOM_PENDING" : "UNKNOWN";
            case CLOSED -> currentResults.size() == 1 && currentResults.get(0).getStatus() == GMatchResultStatus.FINAL_ROOM_CREATED
                    && linksValid && !finals.isEmpty() ? "FINAL_ROOM_CREATED" : "UNKNOWN";
        };
        if ((!currentResults.isEmpty() && Set.of(GTemporaryTeamRoomStatus.OPEN, GTemporaryTeamRoomStatus.READY_CHECK,
                GTemporaryTeamRoomStatus.QUEUE_WAITING).contains(team.getStatus())) || !linksValid || truncated) stage = "UNKNOWN";
        if (team.getStatus() == GTemporaryTeamRoomStatus.READY_CHECK
                && (activeCount != team.getCurrentMemberCount()
                || participants.stream().noneMatch(p -> p.active() && p.userId().equals(team.getLeaderId())))) {
            stage = "UNKNOWN";
            observations.add("저장 인원 또는 방장 참여 기록이 준비 단계의 시작 조건과 맞지 않습니다.");
        }
        if ("FINAL_ROOM_PENDING".equals(stage)) {
            Match match = matches.stream().filter(m -> m.resultId().equals(currentResults.get(0).getId())).findFirst().orElseThrow();
            var opponent = teams.findById(match.opponentTeamId());
            if (!"MATCHED".equals(match.opponentStatus()) || activeCount != team.getTeamSize().getValue()
                    || !Objects.equals(match.opponentActiveMemberCount(), (long) team.getTeamSize().getValue())
                    || opponent.isEmpty() || opponent.get().getTeamSize() != team.getTeamSize()) {
                stage = "UNKNOWN";
                observations.add("양 팀의 상태·규모·현재 참여 인원이 최종방 생성 조건과 맞지 않거나 확인되지 않습니다.");
            }
        }
        if (stage.equals("UNKNOWN")) observations.add("저장된 기록만으로 현재 진행 단계를 확정할 수 없습니다.");
        var queue = queueObserver.observe(team.getTeamSize(), teamId);
        if (team.getStatus() == GTemporaryTeamRoomStatus.QUEUE_WAITING) {
            if (team.getQueuedAt() == null) observations.add("DB 대기 시작 시각이 없습니다.");
            if ("NOT_OBSERVED".equals(queue.presence())) observations.add("DB는 대기 중이지만 Redis 조회 범위에서는 이 팀을 관찰하지 못했습니다.");
        } else if ("FOUND".equals(queue.presence())) observations.add("DB는 대기 상태가 아니지만 Redis 목록에 이 팀이 있습니다.");
        if (queue.occurrences() > 1 || queue.invalidCount() > 0) observations.add("Redis 조회 표본에 중복 팀 또는 해석할 수 없는 항목이 있습니다.");
        return new Detail(summary(team), stage, observations, participants, activeCount, readyCount,
                temporary, matches, finals, truncated, queue, Instant.now());
    }

    public WaitingBoard waiting(Long adminId, int teamSize, Long userId, int malePage, int femalePage, int size) {
        requireAdmin(adminId);
        if ((teamSize != 2 && teamSize != 3) || malePage < 0 || femalePage < 0 || size < 1 || size > 20
                || (userId != null && userId <= 0)) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        GTeamSize format = GTeamSize.from(teamSize);
        var male = diagnostics.waiting(format, GTeamGender.M, userId, PageRequest.of(malePage, size));
        var female = diagnostics.waiting(format, GTeamGender.F, userId, PageRequest.of(femalePage, size));
        var ids = new ArrayList<Long>();
        male.forEach(t -> ids.add(t.getId()));
        female.forEach(t -> ids.add(t.getId()));
        var counts = ids.isEmpty() ? Map.<Long, Long>of() : diagnostics.activeCounts(ids).stream()
                .collect(Collectors.toMap(AdminGroupMatchingRepository.ActiveCount::getTeamId,
                        AdminGroupMatchingRepository.ActiveCount::getMemberCount));
        var queues = queueObserver.observeMany(format, ids);
        LocalDateTime now = LocalDateTime.now();
        java.util.function.Function<GTemporaryTeamRoom, WaitingTeam> row = t -> {
            // Same server wall clock as the stored record, not a reconstructed original wait or ETA.
            Long elapsed = t.getQueuedAt() == null || t.getQueuedAt().isAfter(now)
                    ? null : Duration.between(t.getQueuedAt(), now).getSeconds();
            return new WaitingTeam(summary(t), counts.getOrDefault(t.getId(), 0L), elapsed, queues.get(t.getId()));
        };
        return new WaitingBoard(teamSize, AdminDtos.PageResponse.from(male.map(row)),
                AdminDtos.PageResponse.from(female.map(row)), Instant.now());
    }

    private Team summary(GTemporaryTeamRoom t) {
        return new Team(t.getId(), t.getTeamName(), t.getLeaderId(), t.getTeamSize().getValue(),
                t.getTeamGender().name(), t.getStatus().name(),
                t.getCurrentMemberCount(), t.getCreatedAt(), t.getQueuedAt(), t.getMatchedAt(), t.getClosedAt(), t.getCancelledAt());
    }

    private Room room(Long id) {
        if (id == null) return new Room(null, false, false, null);
        var found = chatRooms.findById(id);
        return new Room(id, found.isPresent(), found.map(r -> r.getType() == ChatRoomType.GROUP).orElse(false),
                found.isPresent() ? chatMembers.countByChatRoomId(id) : null);
    }

    private void requireAdmin(Long id) {
        var admin = id == null ? Optional.<univ.airconnect.user.domain.entity.User>empty() : users.findById(id);
        if (admin.isEmpty() || admin.get().getRole() != UserRole.ADMIN || admin.get().getStatus() != UserStatus.ACTIVE)
            throw new BusinessException(ErrorCode.FORBIDDEN, "활성 관리자만 그룹 진행 기록을 확인할 수 있습니다.");
    }
}
