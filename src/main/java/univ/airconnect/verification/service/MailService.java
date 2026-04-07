package univ.airconnect.verification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import univ.airconnect.verification.exception.VerificationErrorCode;
import univ.airconnect.verification.exception.VerificationException;

import java.nio.charset.StandardCharsets;

@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {

    private static final String VERIFICATION_EMAIL_TEMPLATE_PATH = "html/verification-email.html";
    private static final String VERIFICATION_EMAIL_INLINE_IMAGE_PATH = "static/image-Photoroom.png";
    private static final String VERIFICATION_EMAIL_INLINE_IMAGE_CID = "verification-brand-image";
    private static final String VERIFICATION_EMAIL_TEMPLATE_IMAGE_SRC = "../static/image-Photoroom.png";

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${spring.mail.host:}")
    private String mailHost;

    @Value("${spring.mail.port:-1}")
    private int mailPort;

    public void sendVerificationCode(String to, String code, long expireMinutes, String schoolDomain) {
        validateMailConfiguration();

        try {
            var message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message,
                    MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                    StandardCharsets.UTF_8.name()
            );

            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject("[AirConnect] 이메일 인증 코드");
            helper.setText(
                    buildPlainTextBody(code, expireMinutes, schoolDomain),
                    buildHtmlBody(code, expireMinutes, schoolDomain)
            );
            helper.addInline(
                    VERIFICATION_EMAIL_INLINE_IMAGE_CID,
                    new ClassPathResource(VERIFICATION_EMAIL_INLINE_IMAGE_PATH),
                    "image/png"
            );

            mailSender.send(message);
            log.info("인증 메일 발송 완료. to={}", to);
        } catch (Exception e) {
            log.error("인증 메일 발송 실패. to={}, error={}", to, e.getMessage(), e);
            throw new VerificationException(VerificationErrorCode.MAIL_SEND_FAILED);
        }
    }

    private String buildHtmlBody(String code, long expireMinutes, String schoolDomain) {
        try {
            String template = StreamUtils.copyToString(
                    new ClassPathResource(VERIFICATION_EMAIL_TEMPLATE_PATH).getInputStream(),
                    StandardCharsets.UTF_8
            );

            return template
                    .replace("{{VERIFICATION_CODE}}", code)
                    .replace("{{EXPIRE_MINUTES}}", String.valueOf(expireMinutes))
                    .replace("{{SCHOOL_DOMAIN}}", schoolDomain)
                    .replace(
                            VERIFICATION_EMAIL_TEMPLATE_IMAGE_SRC,
                            "cid:" + VERIFICATION_EMAIL_INLINE_IMAGE_CID
                    );
        } catch (Exception e) {
            log.error("인증 메일 HTML 템플릿 로드 실패. path={}", VERIFICATION_EMAIL_TEMPLATE_PATH, e);
            throw new VerificationException(VerificationErrorCode.MAIL_SEND_FAILED);
        }
    }

    private String buildPlainTextBody(String code, long expireMinutes, String schoolDomain) {
        return """
                AirConnect 이메일 인증 코드

                인증 코드: %s
                유효 시간: %d분
                인증 가능 도메인: @%s

                본인이 요청하지 않았다면 이 메일은 무시하셔도 됩니다.
                """
                .formatted(code, expireMinutes, schoolDomain);
    }

    private void validateMailConfiguration() {
        if (mailHost == null || mailHost.isBlank() || mailPort <= 0) {
            log.error(
                    "메일 설정이 올바르지 않습니다. spring.mail.host='{}', spring.mail.port={}, spring.mail.username='{}'",
                    mailHost,
                    mailPort,
                    fromEmail
            );
            throw new VerificationException(VerificationErrorCode.MAIL_SEND_FAILED);
        }

        if ("localhost".equalsIgnoreCase(mailHost.trim()) && mailPort == 25) {
            log.warn(
                    "메일 설정이 localhost:25 로 확인되었습니다. 의도한 값이 아니라면 SPRING_PROFILES_ACTIVE/MAIL_HOST/MAIL_PORT 를 확인해주세요."
            );
        }
    }
}
