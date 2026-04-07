package univ.airconnect.verification.service;

import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
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
    }

    @Test
    @DisplayName("인증 메일은 HTML 템플릿과 plain text 대체본문으로 발송된다")
    void sendVerificationCode_sendsHtmlTemplateEmail() throws Exception {
        when(mailSender.createMimeMessage()).thenReturn(new JavaMailSenderImpl().createMimeMessage());

        mailService.sendVerificationCode(
                "student@office.hanseo.ac.kr",
                "123456",
                5,
                "office.hanseo.ac.kr"
        );

        ArgumentCaptor<jakarta.mail.internet.MimeMessage> captor =
                ArgumentCaptor.forClass(jakarta.mail.internet.MimeMessage.class);
        verify(mailSender).send(captor.capture());

        jakarta.mail.internet.MimeMessage sentMessage = captor.getValue();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        sentMessage.writeTo(outputStream);
        String rawMessage = outputStream.toString(StandardCharsets.UTF_8);
        String decodedBody = extractBody(sentMessage.getContent());

        assertThat(sentMessage.getSubject()).isEqualTo("[AirConnect] 이메일 인증 코드");
        assertThat(sentMessage.getContentType()).contains("multipart/");
        assertThat(rawMessage).contains("Content-Type: multipart/");
        assertThat(rawMessage).contains("text/html");
        assertThat(rawMessage).contains("image/png");
        assertThat(decodedBody).contains("123456");
        assertThat(decodedBody).contains("office.hanseo.ac.kr");
        assertThat(decodedBody).contains("VERIFICATION CODE");
        assertThat(decodedBody).contains("학교 이메일 인증");
        assertThat(decodedBody).contains("AIRCONNECT");
        assertThat(decodedBody).contains("cid:verification-brand-image");
    }

    private String extractBody(Object content) throws Exception {
        if (content == null) {
            return "";
        }
        if (content instanceof String text) {
            return text;
        }
        if (content instanceof Multipart multipart) {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart bodyPart = multipart.getBodyPart(i);
                builder.append(extractBody(bodyPart.getContent()));
            }
            return builder.toString();
        }
        return String.valueOf(content);
    }
}
