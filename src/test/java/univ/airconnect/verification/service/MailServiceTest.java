package univ.airconnect.verification.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.util.ReflectionTestUtils;

import jakarta.mail.BodyPart;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private MailService mailService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(mailService, "fromEmail", "noreply@airconnect.com");
        ReflectionTestUtils.setField(mailService, "mailHost", "smtp.airconnect.com");
        ReflectionTestUtils.setField(mailService, "mailPort", 587);
        ReflectionTestUtils.setField(mailService, "codeExpirationMinutes", 5L);
        ReflectionTestUtils.setField(mailService, "schoolDomain", "office.hanseo.ac.kr");
    }

    @Test
    @DisplayName("인증 메일은 HTML 템플릿으로 발송된다")
    void sendVerificationCode_sendsHtmlTemplateMail() throws Exception {
        MimeMessage mimeMessage = new JavaMailSenderImpl().createMimeMessage();
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        doNothing().when(mailSender).send(any(MimeMessage.class));

        mailService.sendVerificationCode("student@office.hanseo.ac.kr", "123456");

        verify(mailSender).send(mimeMessage);
        assertThat(mimeMessage.getFrom()).isNotNull();
        assertThat(((InternetAddress) mimeMessage.getFrom()[0]).getAddress()).isEqualTo("noreply@airconnect.com");
        assertThat(mimeMessage.getRecipients(Message.RecipientType.TO)).isNotNull();
        assertThat(((InternetAddress) mimeMessage.getRecipients(Message.RecipientType.TO)[0]).getAddress())
                .isEqualTo("student@office.hanseo.ac.kr");
        assertThat(mimeMessage.getSubject()).isEqualTo("[AirConnect] 이메일 인증 코드");

        String body = extractBody(mimeMessage);
        assertThat(body).contains("123456");
        assertThat(body).contains("5분 동안 유효");
        assertThat(body).contains("@office.hanseo.ac.kr");
        assertThat(body).contains("VERIFICATION CODE");
        assertThat(body).doesNotContain("{{VERIFICATION_CODE}}");
    }

    private String extractBody(MimeMessage message) throws Exception {
        Object content = message.getContent();
        if (content instanceof String contentString) {
            return contentString;
        }
        if (content instanceof Multipart multipart) {
            BodyPart bodyPart = multipart.getBodyPart(0);
            return String.valueOf(bodyPart.getContent());
        }
        return String.valueOf(content);
    }
}
