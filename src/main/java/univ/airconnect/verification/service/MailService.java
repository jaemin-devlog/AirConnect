package univ.airconnect.verification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import univ.airconnect.verification.exception.VerificationErrorCode;
import univ.airconnect.verification.exception.VerificationException;

@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${spring.mail.host:}")
    private String mailHost;

    @Value("${spring.mail.port:-1}")
    private int mailPort;

    public void sendVerificationCode(String to, String code) {
        validateMailConfiguration();

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(to);
        message.setSubject("[AirConnect] 이메일 인증 코드");
        message.setText(
                "AirConnect 회원가입을 위한 인증 코드입니다.\n\n" +
                        "인증 코드: " + code + "\n\n" +
                        "5분 이내에 입력해주세요."
        );

        try {
            mailSender.send(message);
            log.info("Verification email sent successfully. to={}", to);
        } catch (Exception e) {
            log.error("Failed to send verification email. to={}, error={}", to, e.getMessage(), e);
            throw new VerificationException(VerificationErrorCode.MAIL_SEND_FAILED);
        }
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
