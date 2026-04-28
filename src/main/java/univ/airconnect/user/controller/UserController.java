package univ.airconnect.user.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import univ.airconnect.global.response.ApiResponse;
import univ.airconnect.global.security.resolver.CurrentUserId;
import univ.airconnect.user.dto.request.ChangePasswordRequest;
import univ.airconnect.user.dto.request.DeleteAccountRequest;
import univ.airconnect.user.dto.request.SchoolConsentUpsertRequest;
import univ.airconnect.user.dto.request.SignUpRequest;
import univ.airconnect.user.dto.request.UpdateNicknameRequest;
import univ.airconnect.user.dto.request.UpdateProfileRequest;
import univ.airconnect.user.dto.response.ProfileImageUploadResponse;
import univ.airconnect.user.dto.response.SchoolConsentResponse;
import univ.airconnect.user.dto.response.SignUpResponse;
import univ.airconnect.user.dto.response.UpdateNicknameResponse;
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
        log.info("User sign-up requested: userId={}", userId);
        String traceId = (String) httpRequest.getAttribute(TRACE_ID_ATTRIBUTE);
        SignUpResponse response = userService.signUp(userId, request);
        log.info("User sign-up completed: userId={}", userId);
        return ResponseEntity.ok(ApiResponse.ok(response, traceId));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserMeResponse>> getMe(
            @CurrentUserId Long userId,
            HttpServletRequest request
    ) {
        String traceId = (String) request.getAttribute(TRACE_ID_ATTRIBUTE);
        UserMeResponse response = userService.getMe(userId);
        return ResponseEntity.ok(ApiResponse.ok(response, traceId));
    }

    @PatchMapping("/me/nickname")
    public ResponseEntity<ApiResponse<UpdateNicknameResponse>> updateNickname(
            @CurrentUserId Long userId,
            @RequestBody UpdateNicknameRequest request,
            HttpServletRequest httpRequest
    ) {
        log.info("Nickname update requested: userId={}", userId);
        String traceId = (String) httpRequest.getAttribute(TRACE_ID_ATTRIBUTE);
        UpdateNicknameResponse response = userService.updateNickname(userId, request);
        log.info("Nickname update completed: userId={}", userId);
        return ResponseEntity.ok(ApiResponse.ok(response, traceId));
    }

    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getProfile(
            @CurrentUserId Long userId,
            HttpServletRequest request
    ) {
        log.info("User profile requested: userId={}", userId);
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
        log.info("User profile update requested: userId={}", userId);
        String traceId = (String) httpRequest.getAttribute(TRACE_ID_ATTRIBUTE);
        UserProfileResponse response = userService.updateProfile(userId, request);
        log.info("User profile update completed: userId={}", userId);
        return ResponseEntity.ok(ApiResponse.ok(response, traceId));
    }

    @PostMapping(value = "/profile-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ProfileImageUploadResponse>> uploadProfileImage(
            @CurrentUserId Long userId,
            @RequestParam("file") MultipartFile file,
            HttpServletRequest httpRequest
    ) {
        log.info("Profile image upload requested: userId={}", userId);
        String traceId = (String) httpRequest.getAttribute(TRACE_ID_ATTRIBUTE);
        String imageUrl = userProfileImageService.saveProfileImage(userId, file);
        log.info("Profile image upload completed: userId={}", userId);
        return ResponseEntity.ok(ApiResponse.ok(new ProfileImageUploadResponse(imageUrl), traceId));
    }

    @DeleteMapping("/me")
    public ResponseEntity<ApiResponse<Void>> deleteAccount(
            @CurrentUserId Long userId,
            @RequestBody(required = false) DeleteAccountRequest request,
            HttpServletRequest httpRequest
    ) {
        log.info("Account deletion requested: userId={}", userId);
        String traceId = (String) httpRequest.getAttribute(TRACE_ID_ATTRIBUTE);
        userService.deleteAccount(userId, request, traceId);
        log.info("Account deletion completed: userId={}", userId);
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
