package univ.airconnect;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

import org.springframework.beans.factory.annotation.Autowired;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import org.springframework.security.test.context.support.WithMockUser;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.config.location=optional:classpath:/security-audit-no-external-config.yml",
        "spring.datasource.url=jdbc:h2:mem:isolated_application_smoke;MODE=MYSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false",
        "spring.sql.init.mode=never",
        "spring.mail.username=dummy-mail@example.test",
        "jwt.secret=isolated-test-only-jwt-secret-never-use-in-production",
        "jwt.access-token-expiration-seconds=3600",
        "jwt.refresh-token-expiration-seconds=2592000",
        "matching.cache.enabled=false",
        "matching.lock.enabled=false",
        "matching.queue.worker.enabled=false",
        "notification.outbox.worker.enabled=false",
        "notification.push.fcm.enabled=false",
        "auth.admin-account.enabled=false",
        "auth.review-account.enabled=false",
        "auth.social.kakao.enabled=false",
        "apple.revoke.enabled=false",
        "iap.enabled=false",
        "iap.google.verify-enabled=false",
        "openai.compatibility.enabled=false",
        "app.festival-coupons.seed-enabled=false",
        "management.health.redis.enabled=false",
        "management.endpoint.health.probes.enabled=true",
        "management.endpoint.health.group.readiness.include=readinessState,db",
        "management.endpoints.web.exposure.include=health"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AirConnectApplicationTests {

    // Load real application wiring/JPA without contacting Redis, mail, or running background jobs.
    @MockitoBean LettuceConnectionFactory redisConnectionFactory;
    @MockitoBean RedisMessageListenerContainer redisMessageListener;
    @MockitoBean JavaMailSender javaMailSender;
    @MockitoBean ScheduledAnnotationBeanPostProcessor scheduledAnnotationBeanPostProcessor;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void contextLoads() {
    }

    @Test
    void readinessHealthIsPublicAndUp() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void anonymousCannotReadAdminOrOperationsEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/admin/insights")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/chat/ops/snapshot")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    void ordinaryMemberCannotReadAdminOrOperationsEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/admin/insights")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/chat/ops/snapshot")).andExpect(status().isForbidden());
    }

    @Test
    void corsRejectsUnknownWebOriginAndPreservesAdminOrigin() throws Exception {
        mockMvc.perform(options("/api/v1/admin/insights")
                        .header("Origin", "https://untrusted.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        mockMvc.perform(options("/api/v1/admin/insights")
                        .header("Origin", "https://airconnect-admin.web.app")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://airconnect-admin.web.app"));
    }

    @Test
    void defaultSecurityHeadersRemainEnabled() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness").secure(true))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().exists("Strict-Transport-Security"));
    }

}
