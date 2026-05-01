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

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {

    private static final String TEMPLATE_PATH = "html/verification-email.html";
    private static final String LOGO_PATH = "static/image-photoroom.png";
    private static final String LOGO_CONTENT_ID = "airconnect-logo";
    private static final String SUBJECT = "[AirConnect] 이메일 인증 코드";

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${spring.mail.host:}")
    private String mailHost;

    @Value("${spring.mail.port:-1}")
    private int mailPort;

    @Value("${app.verification.code-expiration-minutes:5}")
    private long codeExpirationMinutes;

    @Value("${app.verification.school-domain:office.hanseo.ac.kr}")
    private String schoolDomain;

    public void sendVerificationCode(String to, String code) {
        validateMailConfiguration();

        try {
            String htmlBody = buildHtmlBody(code);
            var message = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(
                    message,
                    MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                    StandardCharsets.UTF_8.name()
            );
            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(SUBJECT);
            helper.setText(htmlBody, true);
            helper.addInline(LOGO_CONTENT_ID, new ClassPathResource(LOGO_PATH), "image/png");
            mailSender.send(message);
            log.info("Verification email sent successfully. to={}", to);
        } catch (Exception e) {
            log.error("Failed to send verification email. to={}, error={}", to, e.getMessage(), e);
            throw new VerificationException(VerificationErrorCode.MAIL_SEND_FAILED);
        }
    }

    private String buildHtmlBody(String code) throws IOException {
        String template = loadTemplate();
        return template
                .replace("{{VERIFICATION_CODE}}", code)
                .replace("{{EXPIRE_MINUTES}}", String.valueOf(codeExpirationMinutes))
                .replace("{{SCHOOL_DOMAIN}}", schoolDomain);
    }

    private String loadTemplate() throws IOException {
        ClassPathResource resource = new ClassPathResource(TEMPLATE_PATH);
        return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
    }

    private void validateMailConfiguration() {
        if (mailHost == null || mailHost.isBlank() || mailPort <= 0) {
            log.error(
                    "Invalid mail configuration. spring.mail.host='{}', spring.mail.port={}, spring.mail.username='{}'",
                    mailHost,
                    mailPort,
                    fromEmail
            );
            throw new VerificationException(VerificationErrorCode.MAIL_SEND_FAILED);
        }

        if ("localhost".equalsIgnoreCase(mailHost.trim()) && mailPort == 25) {
            log.warn(
                    "Mail configuration resolved to localhost:25. If this is unintended, check SPRING_PROFILES_ACTIVE/MAIL_HOST/MAIL_PORT."
            );
        }
    }
}
