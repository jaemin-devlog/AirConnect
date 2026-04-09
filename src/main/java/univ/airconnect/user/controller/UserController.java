package univ.airconnect.user.controller;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import univ.airconnect.global.response.ApiResponse;
import univ.airconnect.global.security.resolver.CurrentUserId;
import univ.airconnect.user.dto.request.DeleteAccountRequest;
import univ.airconnect.user.dto.request.ChangePasswordRequest;
import univ.airconnect.user.dto.request.SchoolConsentUpsertRequest;
import univ.airconnect.user.dto.request.SignUpRequest;
import univ.airconnect.user.dto.request.UpdateProfileRequest;
import univ.airconnect.user.dto.response.ProfileImageUploadResponse;
import univ.airconnect.user.dto.response.SchoolConsentResponse;
import univ.airconnect.user.dto.response.SignUpResponse;
import univ.airconnect.user.dto.response.UserMeResponse;
import univ.airconnect.user.dto.response.UserProfileResponse;
import univ.airconnect.user.service.UserProfileImageService;
import univ.airconnect.user.service.UserSchoolConsentService;
import univ.airconnect.user.service.UserService;


import static univ.airconnect.global.web.TraceIdFilter.TRACE_ID_ATTRIBUTE;

@Slf4j
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserProfileImageService userProfileImageService;
    private final UserSchoolConsentService userSchoolConsentService;

    @PostMapping("/sign-up")
    public ResponseEntity<ApiResponse<SignUpResponse>> signUp(
            @CurrentUserId Long userId,
            @RequestBody SignUpRequest request,
            HttpServletRequest httpRequest
    ) {
        log.info("📝 회원가입 요청: userId={}", userId);
        String traceId = (String) httpRequest.getAttribute(TRACE_ID_ATTRIBUTE);
        SignUpResponse response = userService.signUp(userId, request);
        log.info("✅ 회원가입 완료: userId={}", userId);
        return ResponseEntity.ok(ApiResponse.ok(response, traceId));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserMeResponse>> getMe(
            @CurrentUserId Long userId,
            HttpServletRequest request
    ) {
        log.info("👤 사용자 정보 조회 요청: userId={}", userId);
        String traceId = (String) request.getAttribute(TRACE_ID_ATTRIBUTE);
        UserMeResponse response = userService.getMe(userId);
        log.info("✅ 사용자 정보 조회 완료: userId={}", userId);
        return ResponseEntity.ok(ApiResponse.ok(response, traceId));
    }


    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getProfile(
            @CurrentUserId Long userId,
            HttpServletRequest request
    ) {
        log.info("📖 프로필 조회 요청: userId={}", userId);
        String traceId = (String) request.getAttribute(TRACE_ID_ATTRIBUTE);
        UserProfileResponse response = userService.getProfile(userId);
        return ResponseEntity.ok(ApiResponse.ok(response, traceId));
    }

    @PatchMapping("/profile")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateProfile(
            @CurrentUserId Long userId,
            @RequestBody UpdateProfileRequest request,
            HttpServletRequest httpRequest
    ) {
        log.info("🔄 프로필 업데이트 요청: userId={}", userId);
        String traceId = (String) httpRequest.getAttribute(TRACE_ID_ATTRIBUTE);
        UserProfileResponse response = userService.updateProfile(userId, request);
        log.info("✅ 프로필 업데이트 완료: userId={}", userId);
        return ResponseEntity.ok(ApiResponse.ok(response, traceId));
    }

    @PostMapping(value = "/profile-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ProfileImageUploadResponse>> uploadProfileImage(
            @CurrentUserId Long userId,
            @RequestParam("file") MultipartFile file,
            HttpServletRequest httpRequest
    ) {
        log.info("🖼️ 프로필 이미지 업로드 요청: userId={}", userId);
        String traceId = (String) httpRequest.getAttribute(TRACE_ID_ATTRIBUTE);
        String imageUrl = userProfileImageService.saveProfileImage(userId, file);
        log.info("✅ 프로필 이미지 업로드 완료: userId={}", userId);
        return ResponseEntity.ok(ApiResponse.ok(new ProfileImageUploadResponse(imageUrl), traceId));
    }

    @DeleteMapping("/me")
    public ResponseEntity<ApiResponse<Void>> deleteAccount(
            @CurrentUserId Long userId,
            @RequestBody(required = false) DeleteAccountRequest request,
            HttpServletRequest httpRequest
    ) {
        log.info("🗑️ 회원 탈퇴 요청: userId={}", userId);
        String traceId = (String) httpRequest.getAttribute(TRACE_ID_ATTRIBUTE);
        userService.deleteAccount(userId, request, traceId);
        log.info("🚪 회원 탈퇴 완료: userId={}", userId);
        return ResponseEntity.ok(ApiResponse.ok(null, traceId));
    }

    @PatchMapping("/me/password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @CurrentUserId Long userId,
            @RequestBody ChangePasswordRequest request,
            HttpServletRequest httpRequest
    ) {
        log.info("🔐 비밀번호 변경 요청: userId={}", userId);
        String traceId = (String) httpRequest.getAttribute(TRACE_ID_ATTRIBUTE);
        userService.changePassword(userId, request);
        log.info("✅ 비밀번호 변경 완료: userId={}", userId);
        return ResponseEntity.ok(ApiResponse.ok(null, traceId));
    }

    @GetMapping("/school-consent")
    public ResponseEntity<ApiResponse<SchoolConsentResponse>> getSchoolConsent(
            @CurrentUserId Long userId,
            HttpServletRequest httpRequest
    ) {
        String traceId = (String) httpRequest.getAttribute(TRACE_ID_ATTRIBUTE);
        SchoolConsentResponse response = userSchoolConsentService.get(userId);
        return ResponseEntity.ok(ApiResponse.ok(response, traceId));
    }

    @PutMapping("/school-consent")
    public ResponseEntity<ApiResponse<SchoolConsentResponse>> upsertSchoolConsent(
            @CurrentUserId Long userId,
            @Valid @RequestBody SchoolConsentUpsertRequest request,
            HttpServletRequest httpRequest
    ) {
        String traceId = (String) httpRequest.getAttribute(TRACE_ID_ATTRIBUTE);
        SchoolConsentResponse response = userSchoolConsentService.upsert(userId, request);
        return ResponseEntity.ok(ApiResponse.ok(response, traceId));
    }
}
