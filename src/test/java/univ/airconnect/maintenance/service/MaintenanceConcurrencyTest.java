package univ.airconnect.maintenance.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.MethodParameter;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import univ.airconnect.admin.AdminAuditLogService;
import univ.airconnect.global.error.BusinessException;
import univ.airconnect.global.error.ErrorCode;
import univ.airconnect.global.error.GlobalExceptionHandler;
import univ.airconnect.global.security.resolver.CurrentUserId;
import univ.airconnect.maintenance.controller.MaintenanceAdminController;
import univ.airconnect.maintenance.domain.entity.MaintenanceSetting;
import univ.airconnect.maintenance.dto.response.MaintenanceStatusResponse;
import univ.airconnect.maintenance.repository.MaintenanceSettingRepository;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

/**
 * Only the maintenance entity/repository/service and a dedicated in-memory H2 database are loaded.
 * No Boot application, configuration files, environment files, production resources, external DB,
 * Redis or scheduler are used. Calls have real Spring transaction proxies and real JPA flushes.
 * HTTP tests supply a trusted administrator fixture; authentication itself is outside this suite.
 * Tests deliberately have no surrounding test transaction: winners must commit before assertions.
 */
@SpringJUnitConfig(MaintenanceConcurrencyTest.IsolatedJpaConfig.class)
class MaintenanceConcurrencyTest {
    @Autowired private MaintenanceService service;
    @Autowired private AdminAuditLogService audits;
    @Autowired private ObjectMapper mapper;
    @Autowired private LocalValidatorFactoryBean validator;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @MockitoSpyBean private MaintenanceSettingRepository repository;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        reset(repository, audits);
        repository.deleteAllInBatch();
        mvc = MockMvcBuilders.standaloneSetup(new MaintenanceAdminController(service, audits))
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    @Override public boolean supportsParameter(MethodParameter parameter) {
                        return parameter.hasParameterAnnotation(CurrentUserId.class);
                    }
                    @Override public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
                            NativeWebRequest request, WebDataBinderFactory binderFactory) {
                        return 999L;
                    }
                })
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .setValidator(validator)
                .build();
    }

    @AfterEach
    void cleanUp() {
        reset(repository, audits);
        repository.deleteAllInBatch();
    }

    @Test
    void readingUninitializedStateNeverCreatesSingleton() {
        assertThat(service.getStatus().version()).isEqualTo(-1);
        assertThat(service.getStatus().enabled()).isFalse();
        assertThat(repository.count()).isZero();
        verifyNoInteractions(audits);
    }

    @Test
    void persistedContentNeverChangesEnabledOrStartTimeAndStateNeverChangesContent() {
        var content = service.updateContent(11L, -1, "published title", "published message");
        assertThat(content.version()).isZero();
        assertThat(content.enabled()).isFalse();
        assertThat(content.startedAt()).isNull();

        var enabled = service.changeState(12L, content.version(), true);
        assertThat(enabled.version()).isEqualTo(1);
        assertThat(enabled.title()).isEqualTo("published title");
        assertThat(enabled.message()).isEqualTo("published message");
        var startedAt = service.getStatus().startedAt();

        var edited = service.updateContent(13L, enabled.version(), "new title", "new message");
        assertThat(edited.enabled()).isTrue();
        assertThat(edited.startedAt()).isEqualTo(startedAt);
        assertThat(edited.version()).isEqualTo(2);

        var disabled = service.changeState(14L, edited.version(), false);
        assertThat(disabled.enabled()).isFalse();
        assertThat(disabled.startedAt()).isNull();
        assertThat(disabled.title()).isEqualTo("new title");
        assertThat(disabled.message()).isEqualTo("new message");
        assertThat(disabled.version()).isEqualTo(3);

        var editedWhileDisabled = service.updateContent(15L, disabled.version(), "final title", "final message");
        assertThat(editedWhileDisabled.enabled()).isFalse();
        assertThat(editedWhileDisabled.startedAt()).isNull();
        assertThat(editedWhileDisabled.version()).isEqualTo(4);
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void staleContentAndStateLeavePersistedWinnerUntouched() {
        service.updateContent(11L, -1, "original title", "original body");
        service.changeState(12L, 0, true);
        var winner = service.getStatus();

        assertConflict(() -> service.updateContent(13L, 0, "stale title", "stale body"));
        assertConflict(() -> service.changeState(14L, 0, false));
        assertConflict(() -> service.changeState(14L, -1, false));

        assertThat(service.getStatus()).isEqualTo(winner);
        verifyNoInteractions(audits);
    }

    @Test
    void sameStoredVersionConcurrentRequestsHaveExactlyOneCommitAndOne409AtRealFlush() throws Exception {
        service.updateContent(11L, -1, "original title", "original body");
        var results = raceAfterBothDatabaseReads(
                () -> patchResult("/content", Map.of("expectedVersion", 0, "title", "winner content", "message", "winner body")),
                () -> patchResult("/state", Map.of("expectedVersion", 0, "enabled", true)));

        assertOneSuccessAndOneConflict(results, 1);
        MaintenanceStatusResponse stored = service.getStatus();
        assertThat(stored.version()).isEqualTo(1);
        assertThat(repository.count()).isEqualTo(1);
        if (stored.enabled()) {
            assertThat(stored.title()).isEqualTo("original title");
            assertThat(stored.message()).isEqualTo("original body");
            assertThat(stored.startedAt()).isNotNull();
        } else {
            assertThat(stored.title()).isEqualTo("winner content");
            assertThat(stored.message()).isEqualTo("winner body");
            assertThat(stored.startedAt()).isNull();
        }
    }

    @Test
    void concurrentFirstInsertHasOneVersionZeroSingletonAndOne409WithoutLosingWinner() throws Exception {
        assertThat(service.getStatus().version()).isEqualTo(-1);
        var results = raceAfterBothDatabaseReads(
                () -> patchResult("/content", Map.of("expectedVersion", -1, "title", "first title", "message", "first body")),
                () -> patchResult("/state", Map.of("expectedVersion", -1, "enabled", true)));

        assertOneSuccessAndOneConflict(results, 0);
        var stored = service.getStatus();
        assertThat(repository.count()).isEqualTo(1);
        assertThat(stored.version()).isZero();
        if (stored.enabled()) {
            assertThat(stored.title()).isEqualTo("서버 점검 중");
            assertThat(stored.startedAt()).isNotNull();
        } else {
            assertThat(stored.title()).isEqualTo("first title");
            assertThat(stored.message()).isEqualTo("first body");
            assertThat(stored.startedAt()).isNull();
        }
    }

    @Test
    void boundaryLengthsPersistAndOversizedOrImpossibleVersionsNeverWrite() {
        assertConflict(() -> service.changeState(11L, 0, true));
        assertConflict(() -> service.changeState(11L, -2, true));
        assertThat(repository.count()).isZero();
        var result = service.updateContent(11L, -1, "t".repeat(120), "m".repeat(500));
        assertThat(result.version()).isZero();
        var before = service.getStatus();

        assertThatThrownBy(() -> service.updateContent(12L, 0, "t".repeat(121), "m"))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
        assertThatThrownBy(() -> service.updateContent(12L, 0, "t", "m".repeat(501)))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
        assertThat(service.getStatus()).isEqualTo(before);
    }

    private List<HttpResult> raceAfterBothDatabaseReads(Callable<HttpResult> first, Callable<HttpResult> second)
            throws Exception {
        CyclicBarrier bothRead = new CyclicBarrier(2);
        // Do not fake returned entities or save results. Both real SELECTs finish before either
        // service receives its entity/absence, forcing the actual JPA UPDATE/INSERT race.
        doAnswer(invocation -> {
            // Spring Data's interface proxy has no concrete method for Mockito's
            // callRealMethod. Query through the SAME transaction-bound JPA context.
            var entityManager = EntityManagerFactoryUtils.getTransactionalEntityManager(entityManagerFactory);
            assertThat(entityManager).isNotNull();
            Object result = Optional.ofNullable(entityManager.find(MaintenanceSetting.class, 1L));
            bothRead.await(10, TimeUnit.SECONDS);
            return result;
        }).when(repository).findById(1L);

        var executor = Executors.newFixedThreadPool(2);
        try {
            Future<HttpResult> one = executor.submit(first);
            Future<HttpResult> two = executor.submit(second);
            return List.of(one.get(20, TimeUnit.SECONDS), two.get(20, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
            reset(repository);
        }
    }

    private HttpResult patchResult(String suffix, Map<String, Object> body) throws Exception {
        var response = mvc.perform(patch("/api/v1/admin/maintenance" + suffix)
                        .contentType("application/json").content(mapper.writeValueAsString(body)))
                .andReturn().getResponse();
        return new HttpResult(response.getStatus(), response.getContentAsString());
    }

    private void assertOneSuccessAndOneConflict(List<HttpResult> results, long committedVersion) throws Exception {
        assertThat(results).extracting(HttpResult::status).containsExactlyInAnyOrder(200, 409);
        for (HttpResult result : results) {
            var json = mapper.readTree(result.body());
            if (result.status() == 409) {
                assertThat(json.path("success").asBoolean()).isFalse();
                assertThat(json.path("error").path("code").asText()).isEqualTo(ErrorCode.MAINTENANCE_CONFLICT.getCode());
                assertThat(json.path("data").isMissingNode() || json.path("data").isNull()).isTrue();
            } else {
                assertThat(json.path("success").asBoolean()).isTrue();
                assertThat(json.path("data").path("version").asLong()).isEqualTo(committedVersion);
            }
        }
        // Only the successful committed request reaches the controller's success audit.
        verify(audits, times(1)).record(any(), any(), any(), any(), any(), any(), any());
    }

    private static void assertConflict(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.MAINTENANCE_CONFLICT));
    }

    private record HttpResult(int status, String body) {}

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = MaintenanceSettingRepository.class)
    @Import(MaintenanceService.class)
    static class IsolatedJpaConfig {
        @Bean DataSource dataSource() {
            return new DriverManagerDataSource(
                    "jdbc:h2:mem:maintenance-concurrency-only;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000", "sa", "");
        }

        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
            factory.setPackagesToScan("univ.airconnect.maintenance.domain.entity");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop",
                    "hibernate.jdbc.time_zone", "UTC"));
            return factory;
        }

        @Bean JpaTransactionManager transactionManager(EntityManagerFactory factory) {
            return new JpaTransactionManager(factory);
        }

        @Bean AdminAuditLogService auditService() {
            return mock(AdminAuditLogService.class);
        }

        @Bean ObjectMapper objectMapper() {
            return new ObjectMapper().findAndRegisterModules();
        }

        @Bean LocalValidatorFactoryBean validator() {
            return new LocalValidatorFactoryBean();
        }
    }
}
