package univ.airconnect.user.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import univ.airconnect.user.domain.UserStatus;
import univ.airconnect.user.domain.entity.UserProfile;
import univ.airconnect.user.infrastructure.ProfileImageProperties;
import univ.airconnect.user.repository.UserProfileRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Slf4j
@Controller
@RequestMapping("/api/v1/users/profile-images")
@RequiredArgsConstructor
public class ProfileImageController {

    private static final Pattern SAFE_FILE_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9._-]{1,120}$");

    private final ProfileImageProperties profileImageProperties;
    private final UserProfileRepository userProfileRepository;

    /**
     * 프로필 이미지를 다운로드합니다.
     *
     * @param fileName 이미지 파일 이름
     * @return 이미지 파일 내용
     */
    @GetMapping("/{fileName}")
    public ResponseEntity<byte[]> getProfileImage(@PathVariable String fileName) {
        log.debug("프로필 이미지 조회 요청: key={}", maskFileKey(fileName));
        try {
            if (!isSafeFileName(fileName)) {
                return ResponseEntity.notFound().build();
            }
            if (!isAllowedImageExtension(fileName)) {
                return ResponseEntity.notFound().build();
            }

            UserProfile ownerProfile = userProfileRepository.findByProfileImagePathWithUser(fileName)
                    .orElse(null);
            if (ownerProfile == null || isHiddenByStatus(ownerProfile.getUser().getStatus())) {
                return ResponseEntity.notFound().build();
            }

            Path filePath = resolveSafePath(fileName);
            if (!Files.exists(filePath)) {
                return ResponseEntity.notFound().build();
            }

            byte[] imageData = Files.readAllBytes(filePath);
            MediaType mediaType = resolveMediaType(fileName);
            log.debug("프로필 이미지 제공 완료: key={}, size={}", maskFileKey(fileName), imageData.length);

            return ResponseEntity.ok()
                    .header("X-Content-Type-Options", "nosniff")
                    .cacheControl(CacheControl.maxAge(10, TimeUnit.MINUTES).cachePrivate())
                    .contentType(mediaType)
                    .body(imageData);
        } catch (IOException e) {
            log.warn("프로필 이미지 조회 실패: key={}, reason={}", maskFileKey(fileName), e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    private boolean isSafeFileName(String fileName) {
        return fileName != null
                && !fileName.contains("..")
                && SAFE_FILE_NAME_PATTERN.matcher(fileName).matches();
    }

    private boolean isHiddenByStatus(UserStatus status) {
        if (status == null) {
            return true;
        }
        return profileImageProperties.getProfileImageHiddenUserStatuses().contains(status);
    }

    private Path resolveSafePath(String fileName) throws IOException {
        Path uploadRoot = Paths.get(profileImageProperties.getProfileImageDir()).toAbsolutePath().normalize();
        Path target = uploadRoot.resolve(fileName).normalize();
        if (!target.startsWith(uploadRoot)) {
            throw new IOException("invalid path");
        }
        return target;
    }

    private MediaType resolveMediaType(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return MediaType.IMAGE_JPEG;
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    private boolean isAllowedImageExtension(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        int index = lower.lastIndexOf('.');
        if (index < 0 || index == lower.length() - 1) {
            return false;
        }
        String extension = lower.substring(index + 1);
        if ("jpeg".equals(extension)) {
            extension = "jpg";
        }

        Set<String> allowed = new HashSet<>();
        for (String format : profileImageProperties.getProfileImageAllowedFormats()) {
            if (format == null || format.isBlank()) {
                continue;
            }
            String normalized = format.trim().toLowerCase(Locale.ROOT);
            if (normalized.startsWith(".")) {
                normalized = normalized.substring(1);
            }
            if ("jpeg".equals(normalized)) {
                normalized = "jpg";
            }
            allowed.add(normalized);
        }
        if (allowed.isEmpty()) {
            allowed.add("jpg");
            allowed.add("png");
        }
        return allowed.contains(extension);
    }

    private String maskFileKey(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "-";
        }
        if (fileName.length() <= 8) {
            return "***";
        }
        return fileName.substring(0, 4) + "***" + fileName.substring(fileName.length() - 4);
    }
}
