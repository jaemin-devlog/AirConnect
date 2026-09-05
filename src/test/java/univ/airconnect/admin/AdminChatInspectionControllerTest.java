package univ.airconnect.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.access.ExceptionTranslationFilter;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import univ.airconnect.chat.domain.ChatRoomType;
import univ.airconnect.chat.domain.MessageType;
import univ.airconnect.global.config.TimeConfig;
import univ.airconnect.global.error.ErrorCode;
import univ.airconnect.global.error.GlobalExceptionHandler;
import univ.airconnect.global.security.RestAccessDeniedHandler;
import univ.airconnect.global.security.RestAuthenticationEntryPoint;
import univ.airconnect.global.security.jwt.JwtAuthenticationFilter;
import univ.airconnect.global.security.jwt.JwtProvider;
import univ.airconnect.global.security.resolver.CurrentUserIdArgumentResolver;
import univ.airconnect.user.domain.UserRole;
import univ.airconnect.user.domain.UserStatus;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static univ.airconnect.global.web.TraceIdFilter.TRACE_ID_ATTRIBUTE;

/**
 * HTTP-only slice: real controller, validation, exception advice, JWT filter and ADMIN
 * authorization rule. JWT cryptography, user storage and controller services are mocked.
 * No Spring Boot context, application profiles, database or external transport is loaded.
 */
class AdminChatInspectionControllerTest {
    private static final Long ROOM_ID = 41L;
    private static final Long ADMIN_ID = 101L;
    private static final String FIXTURE_ACCESS_TOKEN = "inspection-controller-fixture-token";
    private static final String TRACE_ID = "inspection-controller-fixture-trace";
    private static final String INSPECTION_PATH = "/api/v1/admin/chat-rooms/{roomId}/message-inspections";
    private static final String MESSAGE_CONTENT = "synthetic inspection message";
    private static final LocalDateTime FROM = LocalDateTime.of(2026, 9, 3, 10, 15, 30);
    private static final LocalDateTime TO = FROM.plusDays(1);

    private AdminService adminService;
    private UserRepository users;
    private JwtProvider jwtProvider;
    private LocalValidatorFactoryBean validator;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        adminService = mock(AdminService.class);
        users = mock(UserRepository.class);
        jwtProvider = mock(JwtProvider.class);

        var mapperBuilder = new Jackson2ObjectMapperBuilder();
        new TimeConfig().timeObjectMapperCustomizer().customize(mapperBuilder);
        ObjectMapper objectMapper = mapperBuilder.build();
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        var translation = new ExceptionTranslationFilter(new RestAuthenticationEntryPoint(objectMapper));
        translation.setAccessDeniedHandler(new RestAccessDeniedHandler(objectMapper));
        // Same ADMIN rule as SecurityConfig, without unrelated maintenance/activity filters.
        var security = new FilterChainProxy(new DefaultSecurityFilterChain(AnyRequestMatcher.INSTANCE,
                new JwtAuthenticationFilter(jwtProvider, users),
                new AnonymousAuthenticationFilter("inspection-controller-fixture-anonymous"),
                translation,
                new AuthorizationFilter(AuthorityAuthorizationManager.hasRole("ADMIN"))));
        var controller = new AdminController(adminService, mock(AdminOperationsService.class),
                mock(AdminAuditLogService.class), mock(AdminUserPurgeService.class));
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new CurrentUserIdArgumentResolver(users))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .setValidator(validator)
                .addFilters(security)
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        validator.close();
    }

    @Test
    void unauthenticatedInspection_isRejectedBeforeServiceInvocation() throws Exception {
        mvc.perform(inspectionRequest("{\"reason\":\"REPORT_REVIEW\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.UNAUTHORIZED.getCode()))
                .andExpect(jsonPath("$.data").value(nullValue()));

        verifyNoInteractions(adminService, users, jwtProvider);
    }

    @Test
    void ordinaryUserInspection_isForbiddenBeforeServiceInvocation() throws Exception {
        authenticate(UserRole.USER);

        mvc.perform(authorized(inspectionRequest("{\"reason\":\"REPORT_REVIEW\"}")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.FORBIDDEN.getCode()))
                .andExpect(jsonPath("$.data").value(nullValue()));

        verifyNoInteractions(adminService);
    }

    @ParameterizedTest
    @EnumSource(AdminRequests.ChatInspectionReason.class)
    void administratorInspection_bindsRequestAndReturnsNoStoreResponse(
            AdminRequests.ChatInspectionReason reason) throws Exception {
        authenticate(UserRole.ADMIN);
        when(adminService.inspectChatMessages(eq(ADMIN_ID), eq(ROOM_ID), any(), eq(TRACE_ID)))
                .thenReturn(inspection(reason));

        mvc.perform(authorized(inspectionRequest("""
                        {"reason":"%s","from":"2026-09-03T10:15:30",
                         "to":"2026-09-04T10:15:30","page":2,"size":25}
                        """.formatted(reason.name()))))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.traceId").value(TRACE_ID))
                .andExpect(jsonPath("$.data.roomId").value(ROOM_ID.intValue()))
                .andExpect(jsonPath("$.data.reason").value(reason.name()))
                .andExpect(jsonPath("$.data.inspectedAt").value("2026-09-04T10:15:30"))
                .andExpect(jsonPath("$.data.messages.page").value(2))
                .andExpect(jsonPath("$.data.messages.size").value(25))
                .andExpect(jsonPath("$.data.messages.items[0].content").value(MESSAGE_CONTENT));

        var body = ArgumentCaptor.forClass(AdminRequests.ChatMessageInspectionRequest.class);
        verify(adminService).inspectChatMessages(eq(ADMIN_ID), eq(ROOM_ID), body.capture(), eq(TRACE_ID));
        assertThat(body.getValue()).isEqualTo(
                new AdminRequests.ChatMessageInspectionRequest(reason, FROM, TO, 2, 25));
        verifyNoMoreInteractions(adminService);
    }

    @Test
    void unknownReason_isBadRequestWithoutInvokingService() throws Exception {
        authenticate(UserRole.ADMIN);

        mvc.perform(authorized(inspectionRequest("{\"reason\":\"UNSUPPORTED_REASON\"}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_REQUEST.getCode()))
                .andExpect(jsonPath("$.data").value(nullValue()));

        verifyNoInteractions(adminService);
    }

    @Test
    void missingReason_isBadRequestWithoutInvokingService() throws Exception {
        authenticate(UserRole.ADMIN);

        mvc.perform(authorized(inspectionRequest("{}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_REQUEST.getCode()))
                .andExpect(jsonPath("$.data").value(nullValue()));

        verifyNoInteractions(adminService);
    }

    @Test
    void legacyRoomDetail_doesNotExposeMessagesEvenWithLegacyPagingParameters() throws Exception {
        authenticate(UserRole.ADMIN);
        when(adminService.getChatRoomDetail(ROOM_ID)).thenReturn(new AdminDtos.ChatRoomDetail(
                roomSummary(), List.of(new AdminDtos.ChatRoomMemberItem(71L, 201L, "fixture-member", FROM, false))));

        mvc.perform(authorized(get("/api/v1/admin/chat-rooms/{roomId}", ROOM_ID)
                        .param("messagePage", "0").param("messageSize", "100")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.room.chatRoomId").value(ROOM_ID.intValue()))
                .andExpect(jsonPath("$.data.members[0].userId").value(201))
                .andExpect(jsonPath("$.data.messages").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.data.room.lastMessage").doesNotHaveJsonPath())
                .andExpect(content().string(not(containsString(MESSAGE_CONTENT))));

        verify(adminService).getChatRoomDetail(ROOM_ID);
        verifyNoMoreInteractions(adminService);
    }

    @Test
    void roomList_doesNotExposeLastMessageOrMessageBodies() throws Exception {
        authenticate(UserRole.ADMIN);
        when(adminService.getChatRooms(0, 20, null, null, null)).thenReturn(
                new AdminDtos.PageResponse<>(List.of(roomSummary()), 0, 20, 1, 1, false));

        mvc.perform(authorized(get("/api/v1/admin/chat-rooms").param("page", "0").param("size", "20")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].chatRoomId").value(ROOM_ID.intValue()))
                .andExpect(jsonPath("$.data.items[0].lastMessage").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.data.items[0].messages").doesNotHaveJsonPath())
                .andExpect(content().string(not(containsString(MESSAGE_CONTENT))));

        verify(adminService).getChatRooms(0, 20, null, null, null);
        verifyNoMoreInteractions(adminService);
    }

    @Test
    void auditStorageFailure_returnsGenericErrorWithoutInspectionOrInternalDetails() throws Exception {
        authenticate(UserRole.ADMIN);
        when(adminService.inspectChatMessages(eq(ADMIN_ID), eq(ROOM_ID), any(), eq(TRACE_ID)))
                .thenThrow(new DataAccessResourceFailureException("synthetic audit-storage-internal-marker"));

        mvc.perform(authorized(inspectionRequest("{\"reason\":\"REPORT_REVIEW\"}")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.INTERNAL_ERROR.getCode()))
                .andExpect(jsonPath("$.error.message").value(ErrorCode.INTERNAL_ERROR.getMessage()))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.data.messages").doesNotHaveJsonPath())
                .andExpect(content().string(not(containsString(MESSAGE_CONTENT))))
                .andExpect(content().string(not(containsString("audit-storage-internal-marker"))));

        verify(adminService).inspectChatMessages(eq(ADMIN_ID), eq(ROOM_ID), any(), eq(TRACE_ID));
        verifyNoMoreInteractions(adminService);
    }

    private void authenticate(UserRole role) {
        Long userId = role == UserRole.ADMIN ? ADMIN_ID : 202L;
        User user = mock(User.class);
        when(user.getRole()).thenReturn(role);
        when(user.getStatus()).thenReturn(UserStatus.ACTIVE);
        when(jwtProvider.getUserId(FIXTURE_ACCESS_TOKEN)).thenReturn(userId);
        when(users.findById(userId)).thenReturn(Optional.of(user));
    }

    private MockHttpServletRequestBuilder authorized(MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + FIXTURE_ACCESS_TOKEN)
                .requestAttr(TRACE_ID_ATTRIBUTE, TRACE_ID);
    }

    private MockHttpServletRequestBuilder inspectionRequest(String body) {
        return post(INSPECTION_PATH, ROOM_ID).contentType(MediaType.APPLICATION_JSON)
                .requestAttr(TRACE_ID_ATTRIBUTE, TRACE_ID).content(body);
    }

    private AdminDtos.ChatMessageInspection inspection(AdminRequests.ChatInspectionReason reason) {
        var message = new AdminDtos.ChatMessageItem(51L, ROOM_ID, 201L, "fixture-sender", MESSAGE_CONTENT,
                MessageType.TEXT, false, null, null, FROM.plusHours(1));
        return new AdminDtos.ChatMessageInspection(ROOM_ID, reason, FROM, TO, TO,
                new AdminDtos.PageResponse<>(List.of(message), 2, 25, 51, 3, false));
    }

    private AdminDtos.ChatRoomSummary roomSummary() {
        return new AdminDtos.ChatRoomSummary(ROOM_ID, "fixture-room", ChatRoomType.PERSONAL,
                301L, 201L, "fixture-member", 202L, "fixture-peer", TO, 2, 1, FROM, TO);
    }
}
