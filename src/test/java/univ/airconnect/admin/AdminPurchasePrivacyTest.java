package univ.airconnect.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.access.ExceptionTranslationFilter;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import univ.airconnect.analytics.repository.AnalyticsEventRepository;
import univ.airconnect.auth.domain.entity.SocialProvider;
import univ.airconnect.chat.repository.ChatMessageRepository;
import univ.airconnect.chat.repository.ChatRoomMemberRepository;
import univ.airconnect.chat.repository.ChatRoomRepository;
import univ.airconnect.global.config.TimeConfig;
import univ.airconnect.global.error.ErrorCode;
import univ.airconnect.global.error.GlobalExceptionHandler;
import univ.airconnect.global.security.RestAccessDeniedHandler;
import univ.airconnect.global.security.RestAuthenticationEntryPoint;
import univ.airconnect.global.security.principal.CustomUserPrincipal;
import univ.airconnect.global.security.resolver.CurrentUserIdArgumentResolver;
import univ.airconnect.iap.domain.IapEnvironment;
import univ.airconnect.iap.domain.IapStore;
import univ.airconnect.iap.domain.entity.IapOrder;
import univ.airconnect.iap.repository.IapOrderRepository;
import univ.airconnect.iap.repository.TicketLedgerRepository;
import univ.airconnect.matching.repository.MatchingConnectionRepository;
import univ.airconnect.moderation.repository.UserReportRepository;
import univ.airconnect.notification.service.NotificationService;
import univ.airconnect.statistics.service.StatisticsService;
import univ.airconnect.user.domain.OnboardingStatus;
import univ.airconnect.user.domain.UserRole;
import univ.airconnect.user.domain.UserStatus;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.repository.UserRepository;
import univ.airconnect.user.service.UserService;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static univ.airconnect.global.web.TraceIdFilter.TRACE_ID_ATTRIBUTE;

/**
 * Real AdminService mapping, controller, JSON serialization and request validation.
 * Authentication is an explicit fixture principal with the production ADMIN authorization
 * rule, not a JWT/production-filter-chain integration test. All repositories, audit storage
 * and external services are mocks; no Boot context, profiles, database or transport is loaded.
 */
class AdminPurchasePrivacyTest {
    private static final Long ADMIN_ID = 101L;
    private static final Long MEMBER_ID = 201L;
    private static final String TRACE_ID = "purchase-privacy-fixture-trace";
    private static final String RESTORE_REASON = "synthetic purchase response regression";
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 9, 3, 10, 15, 30);
    private static final LocalDateTime PROCESSED_AT = CREATED_AT.plusMinutes(1);
    private static final String APPLE_TRANSACTION = "synthetic-private-apple-transaction";
    private static final String APPLE_ORIGINAL_TRANSACTION = "synthetic-private-apple-original";
    private static final String GOOGLE_ORDER = "synthetic-private-google-order";
    private static final String GOOGLE_PURCHASE_TOKEN = "synthetic-private-google-purchase-token";
    private static final String GOOGLE_TOKEN_ONLY = "synthetic-private-google-token-without-order";
    private static final String ACCOUNT_TOKEN = "synthetic-private-account-token";
    private static final String VERIFICATION_HASH = "synthetic-private-verification-hash";
    private static final String MASKED_PAYLOAD = "synthetic-private-masked-payload";
    private static final List<String> PRIVATE_MARKERS = List.of(
            APPLE_TRANSACTION, APPLE_ORIGINAL_TRANSACTION, GOOGLE_ORDER, GOOGLE_PURCHASE_TOKEN,
            GOOGLE_TOKEN_ONLY, ACCOUNT_TOKEN, VERIFICATION_HASH, MASKED_PAYLOAD);

    private UserRepository users;
    private IapOrderRepository orders;
    private NotificationService notifications;
    private UserService userService;
    private AdminAuditLogService audit;
    private AdminService service;
    private ObjectMapper mapper;
    private LocalValidatorFactoryBean validator;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        users = mock(UserRepository.class);
        orders = mock(IapOrderRepository.class);
        notifications = mock(NotificationService.class);
        userService = mock(UserService.class);
        audit = mock(AdminAuditLogService.class);

        var builder = new Jackson2ObjectMapperBuilder();
        new TimeConfig().timeObjectMapperCustomizer().customize(builder);
        mapper = builder.build();
        service = new AdminService(
                mock(AdminNoticeRepository.class), users, mock(UserReportRepository.class),
                mock(MatchingConnectionRepository.class), orders, mock(TicketLedgerRepository.class),
                mock(AnalyticsEventRepository.class), mock(ChatRoomRepository.class),
                mock(ChatRoomMemberRepository.class), mock(ChatMessageRepository.class),
                userService, notifications, mock(StatisticsService.class), mapper, audit);

        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        var translation = new ExceptionTranslationFilter(new RestAuthenticationEntryPoint(mapper));
        translation.setAccessDeniedHandler(new RestAccessDeniedHandler(mapper));
        var authorization = new FilterChainProxy(new DefaultSecurityFilterChain(AnyRequestMatcher.INSTANCE,
                new AnonymousAuthenticationFilter("purchase-privacy-fixture-anonymous"),
                translation, new AuthorizationFilter(AuthorityAuthorizationManager.hasRole("ADMIN"))));
        var controller = new AdminController(service, mock(AdminOperationsService.class), audit,
                mock(AdminUserPurgeService.class));
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new CurrentUserIdArgumentResolver(users))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .setValidator(validator)
                .addFilters(authorization)
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        validator.close();
    }

    @Test
    void realServiceMapping_serializesOnlyInternalPurchaseIdsAndOperationalFields() {
        var purchases = stubPurchases(UserStatus.ACTIVE);

        var detail = service.getUserDetail(MEMBER_ID);
        JsonNode json = mapper.valueToTree(detail);

        assertPurchaseHistory(json.path("purchaseHistories"), purchases);
        assertNoPrivateMarkers(json);
        assertThat(detail.purchaseHistories()).extracting(AdminDtos.PurchaseHistoryItem::orderId)
                .containsExactly(8701L, 8702L, 8703L);
        verify(orders).findTop20ByUserIdOrderByCreatedAtDesc(MEMBER_ID);
        verifyNoMoreInteractions(orders);
        verifyNoInteractions(notifications, userService, audit);
        // Response minimization must not alter the synthetic stored verification identifiers.
        assertThat(purchases.get(0).getTransactionId()).isEqualTo(APPLE_TRANSACTION);
        assertThat(purchases.get(1).getOrderId()).isEqualTo(GOOGLE_ORDER);
        assertThat(purchases.get(2).getPurchaseToken()).isEqualTo(GOOGLE_TOKEN_ONLY);
    }

    @Test
    void administratorGet_returnsSanitizedRealServiceResponse() throws Exception {
        var purchases = stubPurchases(UserStatus.ACTIVE);
        authenticate(UserRole.ADMIN);

        var result = mvc.perform(request("GET"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.traceId").value(TRACE_ID))
                .andExpect(jsonPath("$.data.userId").value(MEMBER_ID.intValue()))
                .andReturn();
        JsonNode json = mapper.readTree(result.getResponse().getContentAsByteArray());

        assertPurchaseHistory(json.path("data").path("purchaseHistories"), purchases);
        assertNoPrivateMarkers(json);
        verify(orders).findTop20ByUserIdOrderByCreatedAtDesc(MEMBER_ID);
        verifyNoMoreInteractions(orders);
        verifyNoInteractions(notifications, userService, audit);
    }

    @Test
    void administratorRestorePatch_returnsSameSanitizedHistoryWithoutExternalSideEffects() throws Exception {
        var purchases = stubPurchases(UserStatus.SUSPENDED);
        var member = users.findById(MEMBER_ID).orElseThrow();
        authenticate(UserRole.ADMIN);

        var result = mvc.perform(request("PATCH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value(MEMBER_ID.intValue()))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andReturn();
        JsonNode json = mapper.readTree(result.getResponse().getContentAsByteArray());

        assertPurchaseHistory(json.path("data").path("purchaseHistories"), purchases);
        assertNoPrivateMarkers(json);
        assertThat(member.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(member.getTickets()).isEqualTo(30);
        verify(orders).findTop20ByUserIdOrderByCreatedAtDesc(MEMBER_ID);
        verifyNoMoreInteractions(orders);
        verify(audit).record(eq(ADMIN_ID), eq(AdminAuditAction.USER_ACTION_APPLIED), eq("USER"),
                eq(MEMBER_ID), anyString(), eq(RESTORE_REASON), anyMap());
        verifyNoInteractions(notifications, userService);
    }

    @Test
    void memberWithoutPurchases_preservesEmptyArrayContract() throws Exception {
        when(users.findById(MEMBER_ID)).thenReturn(Optional.of(member(UserStatus.ACTIVE)));
        when(orders.findTop20ByUserIdOrderByCreatedAtDesc(MEMBER_ID)).thenReturn(List.of());
        authenticate(UserRole.ADMIN);

        mvc.perform(request("GET"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.purchaseHistories").isArray())
                .andExpect(jsonPath("$.data.purchaseHistories").isEmpty());
    }

    @ParameterizedTest(name = "anonymous {0} cannot read purchase history")
    @ValueSource(strings = {"GET", "PATCH"})
    void unauthenticatedRequest_isRejectedBeforeReadingPurchaseRecords(String method) throws Exception {
        mvc.perform(request(method))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.UNAUTHORIZED.getCode()))
                .andExpect(jsonPath("$.data.purchaseHistories").doesNotHaveJsonPath());

        verifyNoInteractions(users, orders, notifications, userService, audit);
    }

    @ParameterizedTest(name = "ordinary user {0} cannot read purchase history")
    @ValueSource(strings = {"GET", "PATCH"})
    void nonAdministrator_isRejectedBeforeReadingPurchaseRecords(String method) throws Exception {
        authenticate(UserRole.USER);

        mvc.perform(request(method))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.FORBIDDEN.getCode()))
                .andExpect(jsonPath("$.data.purchaseHistories").doesNotHaveJsonPath());

        verifyNoInteractions(users, orders, notifications, userService, audit);
    }

    private List<IapOrder> stubPurchases(UserStatus status) {
        when(users.findById(MEMBER_ID)).thenReturn(Optional.of(member(status)));
        var purchases = List.of(
                purchase(8701L, IapStore.APPLE, APPLE_TRANSACTION, APPLE_ORIGINAL_TRANSACTION, null, null),
                purchase(8702L, IapStore.GOOGLE, null, null, GOOGLE_PURCHASE_TOKEN, GOOGLE_ORDER),
                purchase(8703L, IapStore.GOOGLE, null, null, GOOGLE_TOKEN_ONLY, null));
        when(orders.findTop20ByUserIdOrderByCreatedAtDesc(MEMBER_ID)).thenReturn(purchases);
        return purchases;
    }

    private IapOrder purchase(Long id, IapStore store, String transactionId, String originalTransactionId,
                              String purchaseToken, String externalOrderId) {
        IapOrder order = IapOrder.createPending(MEMBER_ID, store, "ticket_10", transactionId,
                originalTransactionId, purchaseToken, externalOrderId, ACCOUNT_TOKEN,
                IapEnvironment.SANDBOX, VERIFICATION_HASH, MASKED_PAYLOAD);
        order.markGranted(10, 20, 30);
        ReflectionTestUtils.setField(order, "id", id);
        ReflectionTestUtils.setField(order, "createdAt", CREATED_AT);
        ReflectionTestUtils.setField(order, "processedAt", PROCESSED_AT);
        return order;
    }

    private User member(UserStatus status) {
        return User.builder().id(MEMBER_ID).provider(SocialProvider.EMAIL)
                .socialId("synthetic-purchase-member").email("purchase-member@example.test")
                .nickname("synthetic member").role(UserRole.USER).status(status)
                .onboardingStatus(OnboardingStatus.FULL).tickets(30).createdAt(CREATED_AT).build();
    }

    private void authenticate(UserRole role) {
        Long userId = role == UserRole.ADMIN ? ADMIN_ID : 301L;
        var principal = new CustomUserPrincipal(userId, role);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities()));
        if (role == UserRole.ADMIN) {
            // Used by the real @CurrentUserId resolver on PATCH; GET relies on the ADMIN rule.
            User admin = User.builder().id(ADMIN_ID).role(UserRole.ADMIN).status(UserStatus.ACTIVE).build();
            when(users.findById(ADMIN_ID)).thenReturn(Optional.of(admin));
        }
    }

    private MockHttpServletRequestBuilder request(String method) {
        MockHttpServletRequestBuilder request = "PATCH".equals(method)
                ? patch("/api/v1/admin/users/{userId}/actions", MEMBER_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"action\":\"REACTIVATE\",\"reason\":\"" + RESTORE_REASON + "\"}")
                : get("/api/v1/admin/users/{userId}", MEMBER_ID);
        return request.requestAttr(TRACE_ID_ATTRIBUTE, TRACE_ID);
    }

    private void assertPurchaseHistory(JsonNode history, List<IapOrder> expected) {
        assertThat(history.isArray()).isTrue();
        assertThat(history.size()).isEqualTo(expected.size());
        for (int i = 0; i < expected.size(); i++) {
            JsonNode item = history.get(i);
            var fieldNames = new ArrayList<String>();
            item.fieldNames().forEachRemaining(fieldNames::add);
            // An exact allowlist also rejects private fields serialized as null.
            assertThat(fieldNames).containsExactlyInAnyOrder("orderId", "store", "productId", "status",
                    "grantedTickets", "beforeTickets", "afterTickets", "processedAt", "createdAt");
            assertThat(item.path("orderId").isIntegralNumber()).isTrue();
            assertThat(item.path("orderId").asLong()).isEqualTo(expected.get(i).getId());
            assertThat(item.path("store").asText()).isEqualTo(expected.get(i).getStore().name());
            assertThat(item.path("productId").asText()).isEqualTo("ticket_10");
            assertThat(item.path("status").asText()).isEqualTo("GRANTED");
            assertThat(item.path("grantedTickets").asInt()).isEqualTo(10);
            assertThat(item.path("beforeTickets").asInt()).isEqualTo(20);
            assertThat(item.path("afterTickets").asInt()).isEqualTo(30);
            assertThat(item.path("createdAt").asText()).isEqualTo("2026-09-03T10:15:30");
            assertThat(item.path("processedAt").asText()).isEqualTo("2026-09-03T10:16:30");
        }
    }

    private void assertNoPrivateMarkers(JsonNode response) {
        assertThat(response.toString()).doesNotContain(PRIVATE_MARKERS.toArray(String[]::new));
    }
}
