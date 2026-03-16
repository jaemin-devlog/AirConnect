package univ.airconnect.user.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@Controller
@RequestMapping("/api/v1/users/profile-images")
@RequiredArgsConstructor
public class ProfileImageController {

    @Value("${app.upload.profile-image-dir:/tmp/airconnect/profile-images}")
    private String uploadDir;

    /**
     * 프로필 이미지를 다운로드합니다.
     *
     * @param fileName 이미지 파일 이름
     * @return 이미지 파일 내용
     */
    @GetMapping("/{fileName}")
    public ResponseEntity<byte[]> getProfileImage(@PathVariable String fileName) {
        log.info("🖼️ 프로필 이미지 다운로드 요청: fileName={}", fileName);

        try {
            // 보안: 상위 경로 접근 방지
            if (fileName.contains("..") || fileName.contains("/")) {
                log.warn("⚠️ 잘못된 파일 요청: fileName={}", fileName);
                return ResponseEntity.badRequest().build();
            }

            Path filePath = Paths.get(uploadDir, fileName);

            // 파일 존재 여부 확인
            if (!Files.exists(filePath)) {
                log.warn("⚠️ 파일을 찾을 수 없음: filePath={}", filePath);
                return ResponseEntity.notFound().build();
            }

            // 파일 읽기
            byte[] imageData = Files.readAllBytes(filePath);
            log.info("✅ 프로필 이미지 제공 완료: fileName={}, size={}", fileName, imageData.length);

            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_JPEG)
                    .body(imageData);
        } catch (IOException e) {
            log.error("❌ 이미지 파일 읽기 중 오류 발생: fileName={}, error={}", fileName, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}

