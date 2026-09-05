package univ.airconnect.maintenance.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.MethodParameter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.bind.support.WebDataBinderFactory;
import univ.airconnect.admin.AdminAuditAction;
import univ.airconnect.admin.AdminAuditLogService;
import univ.airconnect.global.error.BusinessException;
import univ.airconnect.global.error.ErrorCode;
import univ.airconnect.global.error.GlobalExceptionHandler;
import univ.airconnect.global.security.resolver.CurrentUserId;
import univ.airconnect.maintenance.dto.response.MaintenanceStatusResponse;
import univ.airconnect.maintenance.service.MaintenanceService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static univ.airconnect.global.web.TraceIdFilter.TRACE_ID_ATTRIBUTE;

/** Standalone HTTP binding/validation tests at the trusted administrator boundary, not JWT tests. */
class MaintenanceAdminControllerTest {
    private MaintenanceService service;
    private AdminAuditLogService audits;
    private LocalValidatorFactoryBean validator;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(MaintenanceService.class);
        audits = mock(AdminAuditLogService.class);
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
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
                .setMessageConverters(new MappingJackson2HttpMessageConverter(new ObjectMapper().findAndRegisterModules()))
                .setValidator(validator)
                .build();
    }

    @AfterEach
    void tearDown() {
        validator.close();
    }

    @Test
    void getStatusReturnsVersionAndNoStoreWithoutSuccessAudit() throws Exception {
        when(service.getStatus()).thenReturn(response(4, true));
        mvc.perform(get("/api/v1/admin/maintenance"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.version").value(4))
                .andExpect(jsonPath("$.data.enabled").value(true));
        verifyNoInteractions(audits);
    }

    @Test
    void contentEndpointBindsOnlyContentCommandAndAuditsItsFlushedVersion() throws Exception {
        when(service.updateContent(999L, 3, "new title", "new message")).thenReturn(response(4, true));
        mvc.perform(patch("/api/v1/admin/maintenance/content")
                        .requestAttr(TRACE_ID_ATTRIBUTE, "maintenance-http-fixture")
                        .contentType("application/json")
                        .content("{\"expectedVersion\":3,\"title\":\"new title\",\"message\":\"new message\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.traceId").value("maintenance-http-fixture"))
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.version").value(4));
        verify(service).updateContent(999L, 3, "new title", "new message");
        verifyNoMoreInteractions(service);
        assertAudit("CONTENT", 4, true);
    }

    @Test
    void stateEndpointBindsOnlyStateCommandAndAuditsItsFlushedVersion() throws Exception {
        when(service.changeState(999L, 3, false)).thenReturn(response(4, false));
        mvc.perform(patch("/api/v1/admin/maintenance/state").contentType("application/json")
                        .content("{\"expectedVersion\":3,\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.enabled").value(false))
                .andExpect(jsonPath("$.data.version").value(4));
        verify(service).changeState(999L, 3, false);
        verifyNoMoreInteractions(service);
        assertAudit("STATE", 4, false);
    }

    @Test
    void obsoleteCombinedPatchCannotBypassSeparateCommands() throws Exception {
        mvc.perform(patch("/api/v1/admin/maintenance").contentType("application/json")
                        .content("{\"expectedVersion\":3,\"enabled\":false,\"title\":\"stale\",\"message\":\"stale\"}"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.METHOD_NOT_ALLOWED.getCode()));
        verifyNoInteractions(service, audits);
    }

    @Test
    void missingNullOrInvalidVersionAndMissingStateAreRejectedBeforeService() throws Exception {
        for (String body : List.of("{}", "{\"expectedVersion\":null,\"enabled\":true}",
                "{\"expectedVersion\":-2,\"enabled\":true}", "{\"expectedVersion\":0}")) {
            mvc.perform(patch("/api/v1/admin/maintenance/state").contentType("application/json").content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_REQUEST.getCode()));
        }
        for (String body : List.of("{}", "{\"expectedVersion\":null}", "{\"expectedVersion\":-2}")) {
            mvc.perform(patch("/api/v1/admin/maintenance/content").contentType("application/json").content(body))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service, audits);
    }

    @Test
    void oversizedContentIsRejectedBeforeServiceAndAudit() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        for (Map<String, Object> body : List.<Map<String, Object>>of(
                Map.of("expectedVersion", 0, "title", "x".repeat(121), "message", "body"),
                Map.of("expectedVersion", 0, "title", "title", "message", "x".repeat(501)))) {
            mvc.perform(patch("/api/v1/admin/maintenance/content").contentType("application/json")
                            .content(mapper.writeValueAsString(body)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_REQUEST.getCode()));
        }
        verifyNoInteractions(service, audits);
    }

    @Test
    void versionConflictReturns409WithoutSuccessAuditOrData() throws Exception {
        when(service.changeState(999L, 3, false)).thenThrow(new BusinessException(ErrorCode.MAINTENANCE_CONFLICT));
        when(service.updateContent(999L, 3, "title", "body")).thenThrow(new BusinessException(ErrorCode.MAINTENANCE_CONFLICT));
        mvc.perform(patch("/api/v1/admin/maintenance/state").contentType("application/json")
                        .content("{\"expectedVersion\":3,\"enabled\":false}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.MAINTENANCE_CONFLICT.getCode()))
                .andExpect(jsonPath("$.data").isEmpty());
        mvc.perform(patch("/api/v1/admin/maintenance/content").contentType("application/json")
                        .content("{\"expectedVersion\":3,\"title\":\"title\",\"message\":\"body\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
        verifyNoInteractions(audits);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void assertAudit(String operation, long version, boolean enabled) {
        ArgumentCaptor<Map<String, Object>> metadata = ArgumentCaptor.forClass(Map.class);
        verify(audits).record(eq(999L), eq(AdminAuditAction.MAINTENANCE_UPDATED), eq("MAINTENANCE"),
                eq("global"), any(), isNull(), metadata.capture());
        assertThat(metadata.getValue()).containsExactlyInAnyOrderEntriesOf(
                Map.of("operation", operation, "version", version, "enabled", enabled));
    }

    private static MaintenanceStatusResponse response(long version, boolean enabled) {
        LocalDateTime now = LocalDateTime.of(2026, 9, 4, 12, 0);
        return new MaintenanceStatusResponse(enabled, "new title", "new message",
                enabled ? now : null, 999L, now, version);
    }
}
