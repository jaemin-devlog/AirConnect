package univ.airconnect.admin;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import univ.airconnect.global.response.ApiResponse;
import univ.airconnect.global.security.resolver.CurrentUserId;
import univ.airconnect.groupmatching.domain.GTemporaryTeamRoomStatus;

import static univ.airconnect.global.web.TraceIdFilter.TRACE_ID_ATTRIBUTE;

@RestController
@RequestMapping("/api/v1/admin/group-matching/teams")
@RequiredArgsConstructor
public class AdminGroupMatchingController {
    private final AdminGroupMatchingService service;

    @GetMapping
    public ResponseEntity<ApiResponse<AdminDtos.PageResponse<AdminGroupMatchingDtos.Team>>> list(
            @CurrentUserId Long adminId, @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Long teamId,
            @RequestParam(required = false) GTemporaryTeamRoomStatus status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size,
            HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(
                service.list(adminId, userId, teamId, status, page, size), (String) request.getAttribute(TRACE_ID_ATTRIBUTE)));
    }

    @GetMapping("/waiting")
    public ResponseEntity<ApiResponse<AdminGroupMatchingDtos.WaitingBoard>> waiting(
            @CurrentUserId Long adminId, @RequestParam(defaultValue = "2") int teamSize,
            @RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = "0") int malePage, @RequestParam(defaultValue = "0") int femalePage,
            @RequestParam(defaultValue = "5") int size, HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(
                service.waiting(adminId, teamSize, userId, malePage, femalePage, size),
                (String) request.getAttribute(TRACE_ID_ATTRIBUTE)));
    }

    @GetMapping("/{teamId}")
    public ResponseEntity<ApiResponse<AdminGroupMatchingDtos.Detail>> detail(
            @CurrentUserId Long adminId, @PathVariable Long teamId, HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(
                service.detail(adminId, teamId), (String) request.getAttribute(TRACE_ID_ATTRIBUTE)));
    }
}
