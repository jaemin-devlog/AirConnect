package univ.airconnect.user.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import univ.airconnect.global.response.ApiResponse;
import univ.airconnect.global.security.resolver.CurrentUserId;
import univ.airconnect.user.dto.response.UserMeResponse;
import univ.airconnect.user.service.UserService;

import static univ.airconnect.global.web.TraceIdFilter.TRACE_ID_ATTRIBUTE;

@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
public class UserMeAliasController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserMeResponse>> getMe(
            @CurrentUserId Long userId,
            HttpServletRequest request
    ) {
        String traceId = (String) request.getAttribute(TRACE_ID_ATTRIBUTE);
        return ResponseEntity.ok(ApiResponse.ok(userService.getMe(userId), traceId));
    }
}
