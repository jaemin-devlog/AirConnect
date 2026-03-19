package univ.airconnect.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import univ.airconnect.user.domain.MilestoneType;
import univ.airconnect.user.domain.entity.UserMilestone;
import univ.airconnect.user.domain.entity.UserProfile;
import univ.airconnect.user.exception.UserErrorCode;
import univ.airconnect.user.exception.UserException;
import univ.airconnect.user.repository.UserMilestoneRepository;
import univ.airconnect.user.repository.UserProfileRepository;
import univ.airconnect.user.repository.UserRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class UserProfileImageService {

    private final UserProfileRepository userProfileRepository;
    private final UserRepository userRepository;
    private final UserMilestoneRepository userMilestoneRepository;

    @Value("${app.upload.profile-image-dir:/tmp/airconnect/profile-images}")
    private String uploadDir;

    @Value("${app.upload.profile-image-url-base:http://localhost:8080/api/v1/users/profile-images}")
    private String imageUrlBase;

    /**
     * 사용자 프로필 이미지를 저장하고 이미지 URL을 반환합니다.
     *
     * @param userId 사용자 ID
     * @param file 업로드된 이미지 파일
     * @return 이미지 URL
     */
    public String saveProfileImage(Long userId, MultipartFile file) {
        log.info("🖼️ 프로필 이미지 저장 시작: userId={}, filename={}", userId, file.getOriginalFilename());

        // 파일 유효성 검사
        validateImageFile(file);

        try {
            // 업로드 디렉토리 생성
            ensureUploadDirectoryExists();

            // 고유한 파일 이름 생성
            String fileName = generateUniqueFileName(file.getOriginalFilename());

            // 파일 저장
            Path filePath = Paths.get(uploadDir, fileName);
            Files.write(filePath, file.getBytes());
            log.info("✅ 파일 저장 완료: filePath={}", filePath);

            // 이미지 URL
            String imageUrl = imageUrlBase + "/" + fileName;
            log.info("📸 생성된 이미지 URL: {}", imageUrl);
            log.debug("📋 imageUrlBase: {}, fileName: {}", imageUrlBase, fileName);

            // 프로필 정보 업데이트 또는 생성
            UserProfile userProfile = userProfileRepository.findByUserId(userId)
                    .orElseThrow(() -> {
                        log.error("❌ 프로필을 찾을 수 없음: userId={}", userId);
                        return new UserException(UserErrorCode.USER_NOT_FOUND);
                    });

            userProfile.updateProfileImagePath(fileName);
            userProfileRepository.save(userProfile);

            // 마일리지 부여 (중복 부여 방지)
            grantMilestoneIfNotAlreadyGranted(userId, MilestoneType.PROFILE_IMAGE_UPLOADED);

            log.info("✅ 프로필 이미지 경로 업데이트 완료: userId={}, fileName={}, imageUrl={}", userId, fileName, imageUrl);

            return imageUrl;
        } catch (IOException e) {
            log.error("❌ 파일 저장 중 오류 발생: userId={}, error={}", userId, e.getMessage(), e);
            throw new UserException(UserErrorCode.INVALID_INPUT);
        }
    }

    /**
     * 파일이 유효한 이미지 파일인지 검사합니다.
     *
     * @param file 검사할 파일
     */
    private void validateImageFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            log.warn("⚠️ 파일이 비어있습니다");
            throw new UserException(UserErrorCode.INVALID_INPUT);
        }

        // 파일 크기 검사 (최대 10MB)
        long maxFileSize = 10L * 1024 * 1024;
        if (file.getSize() > maxFileSize) {
            log.warn("⚠️ 파일 크기 초과: size={}", file.getSize());
            throw new UserException(UserErrorCode.INVALID_INPUT);
        }

        // 이미지 파일 형식 검사
        String contentType = file.getContentType();
        if (contentType == null || !isValidImageType(contentType)) {
            log.warn("⚠️ 지원하지 않는 파일 형식: contentType={}", contentType);
            throw new UserException(UserErrorCode.INVALID_INPUT);
        }
    }

    /**
     * 지원하는 이미지 형식인지 검사합니다.
     *
     * @param contentType 파일의 MIME 타입
     * @return 지원하는 형식이면 true, 아니면 false
     */
    private boolean isValidImageType(String contentType) {
        return contentType.startsWith("image/jpeg") ||
                contentType.startsWith("image/jpg") ||
                contentType.startsWith("image/png") ||
                contentType.startsWith("image/webp") ||
                contentType.startsWith("image/gif");
    }

    /**
     * 업로드 디렉토리가 존재하지 않으면 생성합니다.
     */
    private void ensureUploadDirectoryExists() throws IOException {
        Path directory = Paths.get(uploadDir);
        if (!Files.exists(directory)) {
            Files.createDirectories(directory);
            log.info("📁 업로드 디렉토리 생성: {}", uploadDir);
        }
    }

    /**
     * 고유한 파일 이름을 생성합니다.
     *
     * @param originalFileName 원본 파일 이름
     * @return 고유한 파일 이름
     */
    private String generateUniqueFileName(String originalFileName) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String uuid = UUID.randomUUID().toString();
        String extension = getFileExtension(originalFileName);
        String fileName = timestamp + "_" + uuid + extension;
        log.debug("📝 생성된 파일 이름: {}", fileName);
        return fileName;
    }

    /**
     * 파일 확장자를 반환합니다.
     *
     * @param fileName 파일 이름
     * @return 확장자 (점 포함)
     */
    private String getFileExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0) {
            return fileName.substring(lastDotIndex).toLowerCase();
        }
        return ".jpg";
    }

    /**
     * 마일리지를 부여합니다. 과거에 같은 마일리스톤이 부여된 적이 없으면 티켓 1개를 추가하고 기록합니다.
     *
     * @param userId 사용자 ID
     * @param milestoneType 마일리스톤 타입
     */
    private void grantMilestoneIfNotAlreadyGranted(Long userId, MilestoneType milestoneType) {
        // 이미 이 마일리스톤이 부여되었고 granted = true인지 확인
        boolean alreadyGranted = userMilestoneRepository.existsByUserIdAndMilestoneTypeAndGrantedTrue(userId, milestoneType);

        if (alreadyGranted) {
            log.info("ℹ️ 이미 지급된 마일리스톤 (중복 부여 방지): userId={}, milestoneType={}", userId, milestoneType);
            return;
        }

        // 마일리스톤 기록 추가
        UserMilestone milestone = UserMilestone.create(userId, milestoneType);
        userMilestoneRepository.save(milestone);

        // 사용자 티켓 1개 추가
        var user = userRepository.findById(userId)
                .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
        user.addTickets(1);

        log.info("🎫 마일리스톤 지급 완료: userId={}, milestoneType={}, 부여 티켓=1, 총 티켓={}", 
                userId, milestoneType, user.getTickets());
    }
}

