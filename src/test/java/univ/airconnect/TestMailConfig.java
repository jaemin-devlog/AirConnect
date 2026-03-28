package univ.airconnect;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

@Configuration
public class TestMailConfig {

    /**
     * test 프로파일에서 ApplicationContext 로딩을 막는 JavaMailSender 미구성 문제를 해결하기 위한 더미 빈.
     * 실제 발송은 수행하지 않으며, MailService 의존성만 만족시킨다.
     */
    @Bean
    @Primary
    public JavaMailSender javaMailSender() {
        return new JavaMailSenderImpl();
    }
}

