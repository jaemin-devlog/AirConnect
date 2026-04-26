package univ.airconnect.maintenance.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import univ.airconnect.maintenance.dto.response.MaintenanceStatusResponse;
import univ.airconnect.maintenance.service.MaintenanceService;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import univ.airconnect.global.security.principal.CustomUserPrincipal;
import univ.airconnect.user.domain.UserRole;

class MaintenanceModeFilterTest {

    private final MaintenanceService maintenanceService = mock(MaintenanceService.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final MaintenanceModeFilter maintenanceModeFilter =
            new MaintenanceModeFilter(maintenanceService, objectMapper);

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void blocksGeneralApiRequestsWhenMaintenanceEnabled() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users/me");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(maintenanceService.isEnabled()).thenReturn(true);
        when(maintenanceService.getStatus()).thenReturn(new MaintenanceStatusResponse(
                true,
                "서버 점검",
                "지금은 접속할 수 없습니다.",
                LocalDateTime.of(2026, 4, 26, 12, 0),
                999L,
                LocalDateTime.of(2026, 4, 26, 12, 1)
        ));

        maintenanceModeFilter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(503);
        Map<?, ?> body = objectMapper.readValue(response.getContentAsByteArray(), Map.class);
        assertThat(body.get("success")).isEqualTo(false);
        assertThat(((Map<?, ?>) body.get("error")).get("code")).isEqualTo("COMMON-503");
        assertThat(((Map<?, ?>) body.get("error")).get("message")).isEqualTo("지금은 접속할 수 없습니다.");
    }

    @Test
    void skipsAdminRoutesEvenWhenMaintenanceEnabled() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/admin/maintenance");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        maintenanceModeFilter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(maintenanceService, never()).isEnabled();
    }

    @Test
    void passesThroughWhenMaintenanceDisabled() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/notices");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(maintenanceService.isEnabled()).thenReturn(false);

        maintenanceModeFilter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
        verify(maintenanceService).isEnabled();
    }

    @Test
    void passesThroughForAuthenticatedAdminEvenWhenMaintenanceEnabled() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/notices");
        MockHttpServletResponse response = new MockHttpServletResponse();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new CustomUserPrincipal(999L, UserRole.ADMIN),
                        null,
                        new CustomUserPrincipal(999L, UserRole.ADMIN).getAuthorities()
                )
        );
        when(maintenanceService.isEnabled()).thenReturn(true);

        maintenanceModeFilter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
        verify(maintenanceService).isEnabled();
        verify(maintenanceService, never()).getStatus();
    }

    @Test
    void passesThroughStatisticsApiForAuthenticatedAdminWhenMaintenanceEnabled() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/statistics/main");
        MockHttpServletResponse response = new MockHttpServletResponse();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new CustomUserPrincipal(1000L, UserRole.ADMIN),
                        null,
                        new CustomUserPrincipal(1000L, UserRole.ADMIN).getAuthorities()
                )
        );
        when(maintenanceService.isEnabled()).thenReturn(true);

        maintenanceModeFilter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
        verify(maintenanceService).isEnabled();
        verify(maintenanceService, never()).getStatus();
    }
}
