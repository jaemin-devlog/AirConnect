package univ.airconnect.verification.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

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
    @DisplayName("인증 메일은 현재 설정대로 plain text 본문으로 발송된다")
    void sendVerificationCode_sendsSimpleMailMessage() {
        mailService.sendVerificationCode("student@office.hanseo.ac.kr", "123456");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());

        SimpleMailMessage message = captor.getValue();
        assertThat(message.getFrom()).isEqualTo("noreply@airconnect.com");
        assertThat(message.getTo()).containsExactly("student@office.hanseo.ac.kr");
        assertThat(message.getSubject()).isEqualTo("[AirConnect] 이메일 인증 코드");
        assertThat(message.getText()).contains("123456");
        assertThat(message.getText()).contains("5분 이내");
    }
}
