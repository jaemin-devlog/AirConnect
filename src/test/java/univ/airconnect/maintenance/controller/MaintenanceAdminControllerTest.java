package univ.airconnect.maintenance.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import univ.airconnect.global.response.ApiResponse;
import univ.airconnect.maintenance.dto.request.MaintenanceUpdateRequest;
import univ.airconnect.maintenance.dto.response.MaintenanceStatusResponse;
import univ.airconnect.maintenance.service.MaintenanceService;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaintenanceAdminControllerTest {

    @Mock
    private MaintenanceService maintenanceService;

    @Mock
    private HttpServletRequest request;

    @Test
    void updateStatus_returnsWrappedMaintenanceStatus() {
        MaintenanceAdminController controller = new MaintenanceAdminController(maintenanceService);
        MaintenanceStatusResponse status = new MaintenanceStatusResponse(
                true,
                "긴급 점검",
                "점검 중입니다.",
                LocalDateTime.of(2026, 4, 26, 15, 0),
                999L,
                LocalDateTime.of(2026, 4, 26, 15, 1)
        );

        when(request.getAttribute("traceId")).thenReturn("trace-maint-admin");
        when(maintenanceService.updateStatus(999L, true, "긴급 점검", "점검 중입니다.")).thenReturn(status);

        ResponseEntity<ApiResponse<MaintenanceStatusResponse>> response = controller.updateStatus(
                999L,
                new MaintenanceUpdateRequest(true, "긴급 점검", "점검 중입니다."),
                request
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData()).isNotNull();
        assertThat(response.getBody().getData().enabled()).isTrue();
        assertThat(response.getBody().getData().title()).isEqualTo("긴급 점검");

        verify(maintenanceService).updateStatus(999L, true, "긴급 점검", "점검 중입니다.");
    }
}
