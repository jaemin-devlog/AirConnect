package univ.airconnect.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import univ.airconnect.user.domain.MilestoneType;
import univ.airconnect.user.domain.UserStatus;
import univ.airconnect.user.domain.entity.UserMilestone;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.domain.entity.UserProfile;
import univ.airconnect.user.exception.UserErrorCode;
import univ.airconnect.user.exception.UserException;
import univ.airconnect.user.infrastructure.MilestoneRewardProperties;
import univ.airconnect.user.infrastructure.ProfileImageProperties;
import univ.airconnect.user.repository.UserMilestoneRepository;
import univ.airconnect.user.repository.UserProfileRepository;
import univ.airconnect.user.repository.UserRepository;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class UserProfileImageService {

    private final UserProfileRepository userProfileRepository;
    private final UserRepository userRepository;
    private final UserMilestoneRepository userMilestoneRepository;
    private final ProfileImageProperties profileImageProperties;
    private final MilestoneRewardProperties milestoneRewardProperties;

    /**
     * 사용자 프로필 이미지를 저장하고 이미지 URL을 반환합니다.
     *
     * @param userId 사용자 ID
     * @param file 업로드된 이미지 파일
     * @return 이미지 URL
     */
    public String saveProfileImage(Long userId, MultipartFile file) {
        log.info("프로필 이미지 저장 시작: userId={}, size={}", userId, file != null ? file.getSize() : null);
        User user = validateUploadableUser(userId);
        SanitizedImage sanitizedImage = validateAndSanitizeImage(file);
        String fileName = null;

        try {
            UserProfile userProfile = userProfileRepository.findByUserId(userId)
                    .orElseThrow(() -> {
                        log.warn("프로필을 찾을 수 없습니다: userId={}", userId);
                        return new UserException(UserErrorCode.USER_NOT_FOUND);
                    });

            String previousFileName = normalizeExistingFileName(userProfile.getProfileImagePath());
            fileName = saveSanitizedImageFile(sanitizedImage);

            userProfile.updateProfileImagePath(fileName);
            userProfileRepository.save(userProfile);
            grantMilestoneIfNotAlreadyGranted(userId, MilestoneType.PROFILE_IMAGE_UPLOADED);
            registerFileCleanupAfterTransaction(fileName, previousFileName);

            String imageUrl = buildImageUrl(fileName);
            log.info("프로필 이미지 저장 완료: userId={}, imageKey={}", user.getId(), maskFileKey(fileName));
            return imageUrl;
        } catch (UserException e) {
            deleteFileQuietly(fileName);
            throw e;
        } catch (Exception e) {
            deleteFileQuietly(fileName);
            log.error("프로필 이미지 저장에 실패했습니다: userId={}, reason={}", userId, e.getMessage());
            throw new UserException(UserErrorCode.PROFILE_IMAGE_STORAGE_ERROR);
        }
    }

    private User validateUploadableUser(Long userId) {
        if (userId == null) {
            throw new UserException(UserErrorCode.USER_NOT_FOUND);
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
        if (user.getStatus() == UserStatus.DELETED) {
            throw new UserException(UserErrorCode.USER_DELETED);
        }
        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new UserException(UserErrorCode.USER_SUSPENDED);
        }
        return user;
    }

    private SanitizedImage validateAndSanitizeImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new UserException(UserErrorCode.PROFILE_IMAGE_EMPTY);
        }

        long maxFileSize = Math.max(1L, profileImageProperties.getProfileImageMaxBytes());
        if (file.getSize() > maxFileSize) {
            throw new UserException(UserErrorCode.PROFILE_IMAGE_TOO_LARGE);
        }

        String contentType = file.getContentType();
        if (contentType != null && !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new UserException(UserErrorCode.PROFILE_IMAGE_UNSUPPORTED_FORMAT);
        }

        String requestedExtension = normalizeExtension(extractExtension(file.getOriginalFilename()));
        if (requestedExtension == null || !allowedExtensions().contains(requestedExtension)) {
            throw new UserException(UserErrorCode.PROFILE_IMAGE_UNSUPPORTED_FORMAT);
        }

        final byte[] rawBytes;
        try {
            rawBytes = file.getBytes();
        } catch (IOException e) {
            throw new UserException(UserErrorCode.PROFILE_IMAGE_CORRUPTED);
        }

        DecodedImage decodedImage = decodeImage(rawBytes);
        if (!allowedExtensions().contains(decodedImage.sourceExtension())) {
            throw new UserException(UserErrorCode.PROFILE_IMAGE_UNSUPPORTED_FORMAT);
        }

        byte[] encoded = reencodeWithoutMetadata(decodedImage.image(), decodedImage.sourceExtension());
        String storageExtension = resolveStorageExtension(decodedImage.image(), decodedImage.sourceExtension());
        return new SanitizedImage(encoded, storageExtension);
    }

    private void ensureUploadDirectoryExists() throws IOException {
        Path directory = uploadRootPath();
        if (!Files.exists(directory)) {
            Files.createDirectories(directory);
        }
    }

    private String saveSanitizedImageFile(SanitizedImage sanitizedImage) throws IOException {
        ensureUploadDirectoryExists();
        IOException lastException = null;
        for (int i = 0; i < 3; i++) {
            String fileName = generateSafeFileName(sanitizedImage.extension());
            Path filePath = resolveSafeFilePath(fileName);
            try {
                Files.write(filePath, sanitizedImage.bytes(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                return fileName;
            } catch (IOException ex) {
                lastException = ex;
            }
        }
        throw lastException == null ? new IOException("failed to persist profile image") : lastException;
    }

    private DecodedImage decodeImage(byte[] rawBytes) {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(rawBytes);
             ImageInputStream imageInputStream = ImageIO.createImageInputStream(bais)) {
            if (imageInputStream == null) {
                throw new UserException(UserErrorCode.PROFILE_IMAGE_CORRUPTED);
            }

            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInputStream);
            if (!readers.hasNext()) {
                throw new UserException(UserErrorCode.PROFILE_IMAGE_CORRUPTED);
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(imageInputStream, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                long maxPixels = Math.max(1L, profileImageProperties.getProfileImageMaxPixels());
                if (width <= 0 || height <= 0 || (long) width * height > maxPixels) {
                    throw new UserException(UserErrorCode.PROFILE_IMAGE_TOO_LARGE);
                }

                BufferedImage image = reader.read(0);
                if (image == null) {
                    throw new UserException(UserErrorCode.PROFILE_IMAGE_CORRUPTED);
                }

                String formatName = normalizeExtension(reader.getFormatName());
                if (formatName == null) {
                    throw new UserException(UserErrorCode.PROFILE_IMAGE_UNSUPPORTED_FORMAT);
                }

                return new DecodedImage(image, formatName);
            } finally {
                reader.dispose();
            }
        } catch (UserException e) {
            throw e;
        } catch (IOException e) {
            throw new UserException(UserErrorCode.PROFILE_IMAGE_CORRUPTED);
        }
    }

    private byte[] reencodeWithoutMetadata(BufferedImage image, String sourceExtension) {
        String outputExtension = resolveStorageExtension(image, sourceExtension);
        BufferedImage imageToEncode = "jpg".equals(outputExtension) ? toRgbImage(image) : image;

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            boolean encoded = ImageIO.write(imageToEncode, outputExtension, baos);
            if (!encoded) {
                throw new UserException(UserErrorCode.PROFILE_IMAGE_UNSUPPORTED_FORMAT);
            }
            return baos.toByteArray();
        } catch (IOException e) {
            throw new UserException(UserErrorCode.PROFILE_IMAGE_CORRUPTED);
        }
    }

    private BufferedImage toRgbImage(BufferedImage source) {
        BufferedImage converted = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = converted.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, source.getWidth(), source.getHeight());
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return converted;
    }

    private String resolveStorageExtension(BufferedImage image, String sourceExtension) {
        Set<String> allowed = allowedExtensions();
        if (image.getColorModel().hasAlpha() && allowed.contains("png")) {
            return "png";
        }
        if (allowed.contains("jpg")) {
            return "jpg";
        }
        if (allowed.contains(sourceExtension)) {
            return sourceExtension;
        }
        throw new UserException(UserErrorCode.PROFILE_IMAGE_UNSUPPORTED_FORMAT);
    }

    private String generateSafeFileName(String extension) {
        String randomPart = UUID.randomUUID().toString().replace("-", "");
        return randomPart + "." + extension;
    }

    private Path uploadRootPath() {
        return Paths.get(profileImageProperties.getProfileImageDir()).toAbsolutePath().normalize();
    }

    private Path resolveSafeFilePath(String fileName) {
        Path root = uploadRootPath();
        Path target = root.resolve(fileName).normalize();
        if (!target.startsWith(root)) {
            throw new UserException(UserErrorCode.INVALID_INPUT);
        }
        return target;
    }

    private void registerFileCleanupAfterTransaction(String newFileName, String previousFileName) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deleteFileQuietly(previousFileName);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                if (previousFileName != null && !previousFileName.equals(newFileName)) {
                    deleteFileQuietly(previousFileName);
                }
            }

            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    deleteFileQuietly(newFileName);
                }
            }
        });
    }

    private void deleteFileQuietly(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return;
        }
        try {
            Path target = resolveSafeFilePath(fileName);
            Files.deleteIfExists(target);
        } catch (Exception ex) {
            log.warn("프로필 이미지 파일 정리에 실패했습니다: imageKey={}, reason={}", maskFileKey(fileName), ex.getMessage());
        }
    }

    private Set<String> allowedExtensions() {
        Set<String> allowed = new HashSet<>();
        for (String format : profileImageProperties.getProfileImageAllowedFormats()) {
            String normalized = normalizeExtension(format);
            if (normalized != null) {
                allowed.add(normalized);
            }
        }
        if (allowed.isEmpty()) {
            allowed.add("jpg");
            allowed.add("png");
        }
        return allowed;
    }

    private String normalizeExtension(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith(".")) {
            normalized = normalized.substring(1);
        }
        if (normalized.isEmpty()) {
            return null;
        }
        if ("jpeg".equals(normalized)) {
            return "jpg";
        }
        return normalized;
    }

    private String extractExtension(String fileName) {
        if (fileName == null) {
            return null;
        }
        int index = fileName.lastIndexOf('.');
        if (index < 0 || index == fileName.length() - 1) {
            return null;
        }
        return fileName.substring(index + 1);
    }

    private String normalizeExistingFileName(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String buildImageUrl(String fileName) {
        String base = profileImageProperties.getProfileImageUrlBase();
        if (base == null || base.isBlank()) {
            return fileName;
        }
        return base.endsWith("/") ? base + fileName : base + "/" + fileName;
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

    private void grantMilestoneIfNotAlreadyGranted(Long userId, MilestoneType milestoneType) {
        // 이미 이 마일리스톤이 부여되었고 granted = true인지 확인
        boolean alreadyGranted = userMilestoneRepository.existsByUserIdAndMilestoneTypeAndGrantedTrue(userId, milestoneType);

        if (alreadyGranted) {
            log.info("ℹ️ 이미 지급된 마일리스톤 (중복 부여 방지): userId={}, milestoneType={}", userId, milestoneType);
            return;
        }

        // 마일리스톤 기록 추가 (동시 요청 레이스로 unique 충돌 가능)
        UserMilestone milestone = UserMilestone.create(userId, milestoneType);
        try {
            userMilestoneRepository.save(milestone);
        } catch (DataIntegrityViolationException e) {
            log.info("ℹ️ 동시 요청으로 이미 지급 처리됨: userId={}, milestoneType={}", userId, milestoneType);
            return;
        }

        int rewardTickets = resolveRewardTickets(milestoneType);
        if (rewardTickets <= 0) {
            log.info("ℹ️ 마일리스톤 기록만 반영됨(티켓 보상 없음): userId={}, milestoneType={}", userId, milestoneType);
            return;
        }

        // 사용자 티켓 추가
        var user = userRepository.findById(userId)
                .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
        user.addTickets(rewardTickets);

        log.info("🎫 마일리스톤 지급 완료: userId={}, milestoneType={}, 부여 티켓={}, 총 티켓={}",
                userId, milestoneType, rewardTickets, user.getTickets());
    }

    private int resolveRewardTickets(MilestoneType milestoneType) {
        if (milestoneType == MilestoneType.PROFILE_IMAGE_UPLOADED) {
            return Math.max(0, milestoneRewardProperties.getProfileImageUploadedTickets());
        }
        if (milestoneType == MilestoneType.EMAIL_VERIFIED) {
            return Math.max(0, milestoneRewardProperties.getEmailVerifiedTickets());
        }
        return 0;
    }

    private record SanitizedImage(byte[] bytes, String extension) {
    }

    private record DecodedImage(BufferedImage image, String sourceExtension) {
    }
}
