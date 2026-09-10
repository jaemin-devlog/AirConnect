package univ.airconnect.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.boot.test.context.TestConfiguration;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.WebResourceSet;
import org.apache.catalina.webresources.EmptyResourceSet;
import org.apache.catalina.webresources.StandardRoot;
import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.descriptor.web.FilterMap;
import org.springframework.context.annotation.*;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.filter.DelegatingFilterProxy;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.config.annotation.*;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import univ.airconnect.analytics.repository.AnalyticsEventRepository;
import univ.airconnect.analytics.service.*;
import univ.airconnect.analytics.web.UserActivityFilter;
import univ.airconnect.auth.controller.AuthController;
import univ.airconnect.auth.domain.entity.SocialProvider;
import univ.airconnect.auth.infrastructure.AdminAccountProperties;
import univ.airconnect.auth.repository.*;
import univ.airconnect.auth.security.TokenHashService;
import univ.airconnect.auth.service.*;
import univ.airconnect.auth.service.oauth.SocialAuthResolver;
import univ.airconnect.auth.service.oauth.apple.AppleAuthClient;
import univ.airconnect.chat.domain.*;
import univ.airconnect.chat.domain.entity.*;
import univ.airconnect.chat.repository.*;
import univ.airconnect.global.config.SecurityConfig;
import univ.airconnect.global.error.GlobalExceptionHandler;
import univ.airconnect.global.security.*;
import univ.airconnect.global.security.jwt.*;
import univ.airconnect.global.security.resolver.CurrentUserIdArgumentResolver;
import univ.airconnect.iap.repository.*;
import univ.airconnect.maintenance.service.MaintenanceService;
import univ.airconnect.moderation.domain.*;
import univ.airconnect.moderation.domain.entity.UserReport;
import univ.airconnect.moderation.repository.UserReportRepository;
import univ.airconnect.notification.domain.*;
import univ.airconnect.notification.domain.entity.PushDevice;
import univ.airconnect.notification.repository.*;
import univ.airconnect.notification.service.*;
import univ.airconnect.statistics.service.StatisticsService;
import univ.airconnect.user.domain.*;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.repository.UserRepository;
import univ.airconnect.user.repository.UserProfileRepository;
import univ.airconnect.user.repository.UserMilestoneRepository;
import univ.airconnect.user.service.UserService;
import univ.airconnect.user.service.UserProfileImageService;
import univ.airconnect.user.service.UserSchoolConsentService;
import univ.airconnect.user.controller.UserController;
import univ.airconnect.chat.service.ChatService;
import univ.airconnect.auth.service.oauth.apple.AppleAccountRevocationService;
import org.springframework.data.redis.core.RedisTemplate;

import javax.sql.DataSource;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Supplier;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/**
 * Opt-in test-classpath server, not a production entry point. Fixed loopback database/user only.
 * No Spring Boot/config discovery/component scanning/schedulers/Redis/mail/FCM clients.
 * All report, chat, audit, notification and outbox repositories are real MySQL/InnoDB.
 * Production login, BCrypt, JWT filter, SecurityConfig, MVC controllers/services are used.
 * Out-of-scope dependencies (Redis throttle/session, analytics, uploads, purge) are mocked.
 */
public final class IsolatedReportMySqlServer implements AutoCloseable {
    static final String JDBC = "jdbc:mysql://127.0.0.1:13389/airconnect_admin_verify?useSSL=false&allowPublicKeyRetrieval=true&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true";
    // Public, disposable fixture credentials, never a real account or environment variable.
    public static final String EMAIL = "operator@airconnect.test";
    public static final String PASSWORD = "Isolated-Report-Only-2026!";
    public static final long ADMIN = 1, REPORTER = 2, SUBJECT = 3;
    final AnnotationConfigWebApplicationContext context;
    final Tomcat tomcat;
    final JdbcTemplate jdbc;
    long reportId, roomId, otherRoomId;

    static DataSource isolatedDataSource() {
        if (!Boolean.getBoolean("airconnect.isolated.mysql"))
            throw new IllegalStateException("Explicit isolated MySQL opt-in required");
        return new DriverManagerDataSource(JDBC, "verify", "isolated-only-not-production");
    }

    static LocalContainerEntityManagerFactoryBean factory(DataSource source, String mode) {
        var factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(source);
        factory.setPackagesToScan("univ.airconnect");
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", mode,
                "hibernate.hbm2ddl.halt_on_error", !"create".equals(mode), "hibernate.jdbc.time_zone", "UTC",
                "hibernate.physical_naming_strategy", "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"));
        return factory;
    }

    public IsolatedReportMySqlServer(int port) throws Exception {
        if (port != 18089) throw new IllegalArgumentException("Only dedicated verification port allowed");
        var source = isolatedDataSource();
        jdbc = new JdbcTemplate(source);
        if (!"airconnect_admin_verify".equals(jdbc.queryForObject("SELECT DATABASE()", String.class)))
            throw new IllegalStateException("Wrong database");
        // Bootstrap unrelated current entities. Rebuild target tables from synthetic legacy fixtures
        // before executing real release migrations, then validate without Hibernate auto-update.
        var bootstrap = factory(source, "create");
        bootstrap.afterPropertiesSet();
        bootstrap.destroy();
        try (var connection = source.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new EncodedResource(new FileSystemResource("src/test/resources/sql/report_legacy_fixture_mysql.sql"), "UTF-8"));
            ScriptUtils.executeSqlScript(connection, new EncodedResource(new FileSystemResource("src/test/resources/sql/admin_release_legacy_fixture_mysql.sql"), "UTF-8"));
            ScriptUtils.executeSqlScript(connection, new EncodedResource(new FileSystemResource("src/main/resources/sql/admin_report_handling_migration_mysql.sql"), "UTF-8"));
            ScriptUtils.executeSqlScript(connection, new EncodedResource(new FileSystemResource("src/main/resources/sql/maintenance_version_migration_mysql.sql"), "UTF-8"));
            ScriptUtils.executeSqlScript(connection, new EncodedResource(new FileSystemResource("src/main/resources/sql/admin_ticket_adjustments_migration_mysql.sql"), "UTF-8"));
            ScriptUtils.executeSqlScript(connection, new EncodedResource(new FileSystemResource("src/main/resources/sql/ticket_history_reference_types_mysql.sql"), "UTF-8"));
        }

        Path base = Path.of("build/isolated-mysql/tomcat").toAbsolutePath();
        Files.createDirectories(base);
        tomcat = new Tomcat();
        tomcat.setBaseDir(base.toString());
        tomcat.setHostname("127.0.0.1");
        tomcat.setPort(port);
        tomcat.getConnector().setProperty("address", "127.0.0.1");
        var servletContext = tomcat.addContext("", base.toString());
        // API-only fixture: expose no filesystem/static resources (the Vite server owns assets).
        servletContext.setResources(new StandardRoot(servletContext) {
            @Override protected WebResourceSet createMainResourceSet() { return new EmptyResourceSet(this); }
        });
        servletContext.setParentClassLoader(getClass().getClassLoader());
        context = new AnnotationConfigWebApplicationContext();
        // Do not expose OS/system properties or discover application*.yml/.env/config keys.
        var environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        context.setEnvironment(environment);
        context.setServletContext(servletContext.getServletContext());
        context.register(Config.class);
        context.refresh();
        servletContext.getServletContext().setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, context);
        var wrapper = Tomcat.addServlet(servletContext, "dispatcher", new DispatcherServlet(context));
        wrapper.setLoadOnStartup(1);
        servletContext.addServletMappingDecoded("/", "dispatcher");
        var filter = new FilterDef();
        filter.setFilterName("security");
        filter.setFilter(new DelegatingFilterProxy("springSecurityFilterChain", context));
        servletContext.addFilterDef(filter);
        var mapping = new FilterMap();
        mapping.setFilterName("security"); mapping.addURLPattern("/*");
        servletContext.addFilterMap(mapping);
        seed();
        tomcat.start();
        System.out.println("ISOLATED_REPORT_READY http://127.0.0.1:" + port + " report=" + reportId
                + " room=" + roomId + " mysql=" + jdbc.queryForObject("SELECT VERSION()", String.class));
    }

    <T> T tx(Supplier<T> task) {
        return new TransactionTemplate(context.getBean(JpaTransactionManager.class)).execute(status -> task.get());
    }

    private void seed() {
        tx(() -> {
            var users = context.getBean(UserRepository.class);
            var admin = User.createEmailUser(EMAIL, context.getBean(PasswordEncoder.class).encode(PASSWORD));
            admin.changeRole(UserRole.ADMIN);
            admin.completeSignUp("격리 검증 관리자", "검증 관리자", 99999999, "가상 학과");
            users.saveAndFlush(admin);
            users.saveAndFlush(user("가상 신고자"));
            users.saveAndFlush(user("가상 대상 회원"));
            var rooms = context.getBean(ChatRoomRepository.class);
            roomId = rooms.saveAndFlush(ChatRoom.create("격리 검증 대화방", ChatRoomType.GROUP)).getId();
            otherRoomId = rooms.saveAndFlush(ChatRoom.create("무관한 가상 대화방", ChatRoomType.GROUP)).getId();
            var members = context.getBean(ChatRoomMemberRepository.class);
            members.save(ChatRoomMember.create(rooms.findById(roomId).orElseThrow(), users.findById(REPORTER).orElseThrow()));
            members.save(ChatRoomMember.create(rooms.findById(roomId).orElseThrow(), users.findById(SUBJECT).orElseThrow()));
            var messages = context.getBean(ChatMessageRepository.class);
            messages.save(ChatMessage.create(roomId, REPORTER, "가상 신고자", "문의 재현용 가상 대화입니다.", MessageType.TEXT));
            var deleted = ChatMessage.create(roomId, SUBJECT, "가상 대상 회원", "삭제 원문 가상 자료 <script>alert('fixture')</script>", MessageType.TEXT);
            deleted.softDelete();
            long message = messages.saveAndFlush(deleted).getId();
            messages.save(ChatMessage.create(roomId, SUBJECT, "가상 대상 회원", "추가 가상 메시지 🛫", MessageType.TEXT));
            // More than one real API page, so browser return-navigation can be verified.
            for (int index = 1; index <= 11; index++) {
                context.getBean(UserReportRepository.class).saveAndFlush(UserReport.createReceived(REPORTER,
                        SUBJECT, ReportReasonCode.HARASSMENT, "페이지 이동 검증용 가상 신고 " + index,
                        ReportSourceType.CHAT_ROOM, Long.toString(roomId)));
            }
            reportId = context.getBean(UserReportRepository.class).saveAndFlush(UserReport.createReceived(REPORTER,
                    SUBJECT, ReportReasonCode.HARASSMENT, "브라우저와 MySQL 통합 검증용 신고입니다.",
                    ReportSourceType.CHAT_MESSAGE, Long.toString(message))).getId();
            var device = PushDevice.register(REPORTER, "synthetic-device", PushPlatform.ANDROID, PushProvider.FCM,
                    "synthetic-not-a-deliverable-push-token", null, true, "test", "test", "ko", "UTC", LocalDateTime.now());
            context.getBean(PushDeviceRepository.class).saveAndFlush(device);
            return null;
        });
    }

    static User user(String name) {
        return User.builder().provider(SocialProvider.APPLE).socialId(UUID.randomUUID().toString())
                .nickname(name).name(name).deptName("가상 학과").role(UserRole.USER).status(UserStatus.ACTIVE)
                .onboardingStatus(OnboardingStatus.FULL).tickets(10).createdAt(LocalDateTime.now()).build();
    }

    @Override public void close() throws Exception { tomcat.stop(); tomcat.destroy(); context.close(); }

    public static void main(String[] args) throws Exception {
        var server = new IsolatedReportMySqlServer(18089);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { server.close(); } catch (Exception ignored) { /* isolated teardown */ }
        }));
        server.tomcat.getServer().await();
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackages = {"univ.airconnect.admin", "univ.airconnect.user.repository",
            "univ.airconnect.moderation.repository", "univ.airconnect.chat.repository", "univ.airconnect.matching.repository",
            "univ.airconnect.notification.repository", "univ.airconnect.iap.repository", "univ.airconnect.analytics.repository",
            "univ.airconnect.maintenance.repository", "univ.airconnect.groupmatching.repository"})
    @Import({AuthController.class, AuthService.class, AdminAccountService.class, JwtProvider.class,
            JwtAuthenticationFilter.class, SecurityConfig.class, VerifiedSchoolEmailFilter.class, UserActivityFilter.class,
            RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class, GlobalExceptionHandler.class,
            AdminReportService.class, AdminReportController.class, AdminService.class, AdminController.class,
            AdminAuditLogService.class, AdminApiAuditInterceptor.class, NotificationService.class, UserController.class,
            AdminTicketAdjustmentController.class, AdminTicketAdjustmentService.class,
            AdminGroupMatchingController.class, AdminGroupMatchingService.class,
            univ.airconnect.maintenance.controller.MaintenanceAdminController.class, MaintenanceService.class})
    static class Config {
        @Bean DataSource dataSource() { return isolatedDataSource(); }
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource source) { return factory(source, "validate"); }
        @Bean JpaTransactionManager transactionManager(EntityManagerFactory factory) { return new JpaTransactionManager(factory); }
        @Bean ObjectMapper objectMapper() { return new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS); }
        @Bean WebMvcConfigurer mvc(UserRepository users, AdminApiAuditInterceptor audit, ObjectMapper mapper) {
            return new WebMvcConfigurer() {
                @Override public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
                    resolvers.add(new CurrentUserIdArgumentResolver(users));
                }
                @Override public void addInterceptors(InterceptorRegistry registry) { registry.addInterceptor(audit).addPathPatterns("/api/v1/admin/**"); }
                @Override public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
                    converters.forEach(c -> { if (c instanceof MappingJackson2HttpMessageConverter json) json.setObjectMapper(mapper); });
                }
            };
        }
        @Bean AdminAccountProperties adminAccountProperties() { return new AdminAccountProperties(true, EMAIL, PASSWORD, "검증", "검증", "가상", 99999999); }
        @Bean JwtProperties jwtProperties() { return new JwtProperties(UUID.randomUUID() + "-" + UUID.randomUUID(), 3600, 3600); }
        @Bean TokenHashService tokenHashService() { return new TokenHashService("isolated-fixture"); }
        @Bean RefreshTokenRepository refreshTokenRepository() {
            var mock = mock(RefreshTokenRepository.class); when(mock.findByUserId(anyLong())).thenReturn(List.of()); return mock;
        }
        @Bean AttemptThrottleService throttle() { return mock(AttemptThrottleService.class); }
        @Bean SocialAuthResolver socialAuthResolver() { return mock(SocialAuthResolver.class); }
        @Bean AppleAuthClient appleAuthClient() { return mock(AppleAuthClient.class); }
        @Bean SocialLoginDeviceBindingRepository bindings() { return mock(SocialLoginDeviceBindingRepository.class); }
        @Bean UserService userService(UserRepository users, UserProfileRepository profiles, UserMilestoneRepository milestones,
                RefreshTokenRepository refresh, AnalyticsService analytics, PushDeviceRepository devices) {
            return new UserService(users, profiles, milestones, refresh, analytics, mock(ChatService.class), devices,
                    mock(RedisTemplate.class), mock(AppleAccountRevocationService.class),
                    mock(univ.airconnect.department.repository.DepartmentRepository.class));
        }
        @Bean UserProfileImageService profileImages() { return mock(UserProfileImageService.class); }
        @Bean UserSchoolConsentService schoolConsent() { return mock(UserSchoolConsentService.class); }
        @Bean AnalyticsService analyticsService() { return mock(AnalyticsService.class); }
        @Bean UserActivityService activityService() { return mock(UserActivityService.class); }
        @Bean AdminGroupQueueObserver groupQueueObserver() { return mock(AdminGroupQueueObserver.class); }
        @Bean StatisticsService statisticsService() { return mock(StatisticsService.class); }
        @Bean AdminOperationsService operations() { return mock(AdminOperationsService.class); }
        @Bean AdminUserPurgeService purge() { return mock(AdminUserPurgeService.class); }
        @Bean NotificationPreferenceService preferences() {
            var mock = mock(NotificationPreferenceService.class);
            when(mock.getDeliveryPolicy(anyLong(), any())).thenReturn(new NotificationPreferenceService.DeliveryPolicy(true, true));
            return mock;
        }
        @Bean PushDeviceService devices(PushDeviceRepository repository) {
            var mock = mock(PushDeviceService.class);
            when(mock.findPushableDevices(anyLong())).thenAnswer(call -> repository.findAll().stream()
                    .filter(device -> device.getUserId().equals(call.getArgument(0))).toList());
            return mock;
        }
    }
}
