package univ.airconnect.admin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import univ.airconnect.maintenance.controller.MaintenanceAdminController;
import univ.airconnect.maintenance.dto.request.MaintenanceContentUpdateRequest;
import univ.airconnect.maintenance.dto.request.MaintenanceStateUpdateRequest;
import univ.airconnect.maintenance.dto.response.MaintenanceStatusResponse;
import univ.airconnect.maintenance.service.MaintenanceService;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Cross-controller registration contract for the shipped admin frontend.
 * No Boot configuration, credentials, database, network, scheduler or business operation.
 * Service/auth behavior is covered by the existing security and transaction suites.
 */
class AdminReleaseContractTest {
    private Set<String> routes;
    private Object[] dependencies;

    @BeforeEach
    void registerAllControllersTogether() {
        var admin = mock(AdminService.class);
        var operations = mock(AdminOperationsService.class);
        var audit = mock(AdminAuditLogService.class);
        var purge = mock(AdminUserPurgeService.class);
        var groups = mock(AdminGroupMatchingService.class);
        var reports = mock(AdminReportService.class);
        var tickets = mock(AdminTicketAdjustmentService.class);
        var maintenance = mock(MaintenanceService.class);
        dependencies = new Object[]{admin, operations, audit, purge, groups, reports, tickets, maintenance};
        var mvc = MockMvcBuilders.standaloneSetup(
                new AdminController(admin, operations, audit, purge),
                new AdminGroupMatchingController(groups),
                new AdminReportController(reports),
                new AdminTicketAdjustmentController(tickets),
                new MaintenanceAdminController(maintenance, audit)).build();
        var mapping = mvc.getDispatcherServlet().getWebApplicationContext()
                .getBean(RequestMappingHandlerMapping.class);
        routes = mapping.getHandlerMethods().keySet().stream()
                .flatMap(info -> info.getMethodsCondition().getMethods().stream()
                        .flatMap(method -> info.getPatternValues().stream().map(path -> method + " " + path)))
                .collect(Collectors.toSet());
        verifyNoInteractions(dependencies);
    }

    @Test
    void newAdminScreensHaveRegisteredEndpoints() {
        assertThat(routes).contains(
                "GET /api/v1/admin/chat-rooms",
                "GET /api/v1/admin/chat-rooms/{roomId}",
                "POST /api/v1/admin/chat-rooms/{roomId}/message-inspections",
                "GET /api/v1/admin/group-matching/teams",
                "GET /api/v1/admin/group-matching/teams/waiting",
                "GET /api/v1/admin/group-matching/teams/{teamId}",
                "GET /api/v1/admin/reports/{reportId}",
                "PATCH /api/v1/admin/reports/{reportId}",
                "POST /api/v1/admin/reports/{reportId}/evidence-inspections",
                "POST /api/v1/admin/tickets/adjustments",
                "GET /api/v1/admin/tickets/adjustments/{operationId}",
                "GET /api/v1/admin/maintenance",
                "PATCH /api/v1/admin/maintenance/content",
                "PATCH /api/v1/admin/maintenance/state");
    }

    @Test
    void unversionedMaintenanceWriteCannotBypassSplitEndpoints() {
        assertThat(routes).doesNotContain("PATCH /api/v1/admin/maintenance",
                "PUT /api/v1/admin/maintenance", "POST /api/v1/admin/maintenance");
        assertThat(fields(MaintenanceContentUpdateRequest.class))
                .containsExactlyInAnyOrder("expectedVersion", "title", "message");
        assertThat(fields(MaintenanceStateUpdateRequest.class))
                .containsExactlyInAnyOrder("expectedVersion", "enabled");
        assertThat(fields(MaintenanceStatusResponse.class)).contains("version", "enabled");
    }

    @Test
    void ticketContractRequiresStableOperationIdAndReturnsReceipt() {
        assertThat(fields(AdminRequests.TicketAdjustmentRequest.class))
                .containsExactlyInAnyOrder("operationId", "userId", "amount", "reason");
        assertThat(fields(AdminDtos.TicketAdjustmentResult.class))
                .contains("operationId", "status");
    }

    @Test
    void reportWritesKeepVersionAndInternalMemoSeparateFromReply() {
        assertThat(fields(AdminRequests.ReportStatusUpdateRequest.class))
                .containsExactlyInAnyOrder("expectedVersion", "status", "internalMemo", "reporterReply");
    }

    @Test
    void basicChatDetailsDoNotReturnConversationBodies() {
        assertThat(fields(AdminDtos.ChatRoomDetail.class)).doesNotContain("messages", "lastMessage");
        assertThat(fields(AdminDtos.ChatRoomSummary.class)).doesNotContain("lastMessage", "content");
    }

    private Set<String> fields(Class<?> record) {
        assertThat(record.isRecord()).isTrue();
        return Arrays.stream(record.getRecordComponents()).map(RecordComponent::getName)
                .collect(Collectors.toSet());
    }
}
