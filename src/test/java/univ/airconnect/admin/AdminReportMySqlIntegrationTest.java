package univ.airconnect.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import univ.airconnect.global.security.jwt.JwtProvider;
import univ.airconnect.moderation.domain.*;
import univ.airconnect.moderation.domain.entity.UserReport;
import univ.airconnect.moderation.repository.UserReportRepository;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Actual MySQL + actual loopback HTTP, not MockMvc/H2. Off by default. */
@EnabledIfSystemProperty(named = "airconnect.isolated.mysql", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AdminReportMySqlIntegrationTest {
    private IsolatedReportMySqlServer server;
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private String token;
    private static final String PREFIX = "/api/v1/admin/reports/";

    @BeforeAll void start() throws Exception {
        server = new IsolatedReportMySqlServer(18089);
        var login = call("POST", "/api/v1/auth/admin/login", null,
                Map.of("email", IsolatedReportMySqlServer.EMAIL, "password", IsolatedReportMySqlServer.PASSWORD, "deviceId", "isolated-http"));
        assertThat(login.statusCode()).isEqualTo(200);
        token = json.readTree(login.body()).path("accessToken").asText();
        assertThat(token.isBlank()).isFalse(); // Never print or persist the generated credential.
        assertThat(json.readTree(login.body()).path("refreshToken").asText()).isNotBlank();
        var me = call("GET", "/api/v1/users/me", token, null);
        assertThat(me.statusCode()).isEqualTo(200);
        assertThat(json.readTree(me.body()).path("data").path("role").asText()).isEqualTo("ADMIN");
    }
    @AfterAll void stop() throws Exception { if (server != null) server.close(); }

    @Test void automaticHistoryHasNoDateGateUsesCursorAndPreservesConversation() throws Exception {
        var before = conversationState();
        String path = "/api/v1/admin/chat-rooms/" + server.roomId + "/message-history";
        assertThat(call("POST", path, null, Map.of()).statusCode()).isEqualTo(401);
        String ordinary = server.context.getBean(JwtProvider.class).createAccessToken(IsolatedReportMySqlServer.REPORTER);
        assertThat(call("POST", path, ordinary, Map.of()).statusCode()).isEqualTo(403);
        var first = call("POST", path, token, Map.of("size", 2));
        assertThat(first.statusCode()).isEqualTo(200);
        assertThat(first.headers().firstValue("Cache-Control")).hasValue("no-store");
        JsonNode data = json.readTree(first.body()).path("data");
        assertThat(data.path("items").size()).isEqualTo(2);
        assertThat(data.path("hasMore").asBoolean()).isTrue();
        long cursor = data.path("nextBeforeId").asLong();
        var next = call("POST", path, token, Map.of("beforeId", cursor, "size", 2));
        assertThat(next.statusCode()).isEqualTo(200);
        assertThat(json.readTree(next.body()).path("data").path("hasMore").asBoolean()).isFalse();
        assertThat(first.body() + next.body()).contains("삭제 원문", "alert(");
        assertThat(call("POST", PREFIX + server.reportId + "/evidence-history", token,
                Map.of("roomId", server.otherRoomId)).statusCode()).isEqualTo(403);
        assertThat(call("POST", PREFIX + server.reportId + "/evidence-history", token,
                Map.of("roomId", server.roomId)).statusCode()).isEqualTo(200);
        assertThat(conversationState()).isEqualTo(before);
    }

    @Test void automaticHistoryAuditFailureReturnsNoContent() throws Exception {
        var before = conversationState();
        server.jdbc.execute("CREATE TRIGGER reject_history_fixture BEFORE INSERT ON admin_audit_logs FOR EACH ROW BEGIN IF NEW.action = 'CHAT_MESSAGES_INSPECTED' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='synthetic history audit failure'; END IF; END");
        try {
            var result = call("POST", "/api/v1/admin/chat-rooms/" + server.roomId + "/message-history", token, Map.of());
            assertThat(result.statusCode()).isEqualTo(500);
            assertThat(result.body()).doesNotContain("삭제 원문", "alert(");
            assertThat(conversationState()).isEqualTo(before);
        } finally { server.jdbc.execute("DROP TRIGGER reject_history_fixture"); }
    }

    @Test void migrationPreservesLegacyRowsAndBackfillsOnlyTerminalRows() {
        assertThat(server.jdbc.queryForObject("SELECT VERSION()", String.class)).startsWith("8.0.");
        assertThat(server.jdbc.queryForObject("SELECT @@transaction_isolation", String.class)).isEqualTo("REPEATABLE-READ");
        assertThat(server.jdbc.queryForObject("SELECT detail FROM user_reports WHERE id=1", String.class)).isEqualTo("이전 신고 가상 자료 🛫");
        assertThat(server.jdbc.queryForObject("SELECT COUNT(*) FROM user_reports WHERE id IN(1,3) AND completed_at=updated_at AND version=0 AND internal_memo IS NULL AND reporter_reply IS NULL", Integer.class)).isEqualTo(2);
        assertThat(server.jdbc.queryForObject("SELECT completed_at FROM user_reports WHERE id=2", String.class)).isNull();
        assertThat(server.jdbc.queryForObject("SELECT JSON_EXTRACT(metadata_json,'$.legacy') FROM admin_audit_logs WHERE id=1", String.class)).isEqualTo("true");
        assertThat(server.jdbc.queryForList("SELECT column_name FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='admin_audit_logs' AND index_name='idx_admin_audit_report_created' ORDER BY seq_in_index", String.class))
                .containsExactly("report_id", "created_at");
        assertThat(server.jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN('user_reports','admin_audit_logs') AND engine='InnoDB'", Integer.class)).isEqualTo(2);
    }

    @Test void realJwtAdminBoundary() throws Exception {
        assertThat(call("GET", PREFIX + server.reportId, null, null).statusCode()).isEqualTo(401);
        assertThat(call("GET", PREFIX + server.reportId, "invalid-fixture", null).statusCode()).isEqualTo(401);
        String ordinary = server.context.getBean(JwtProvider.class).createAccessToken(IsolatedReportMySqlServer.REPORTER);
        assertThat(call("GET", PREFIX + server.reportId, ordinary, null).statusCode()).isEqualTo(403);
        assertThat(call("GET", PREFIX + server.reportId, token, null).statusCode()).isEqualTo(200);
        assertThat(call("POST", "/api/v1/auth/admin/login", null, Map.of("email", IsolatedReportMySqlServer.EMAIL,
                "password", "wrong-fixture", "deviceId", "isolated-http")).statusCode()).isEqualTo(401);
    }

    @ParameterizedTest @ValueSource(strings = {"SUSPENDED", "RESTRICTED", "DELETED"})
    void existingJwtChecksCurrentDbStatus(String status) throws Exception {
        try {
            server.jdbc.update("UPDATE users SET status=? WHERE id=1", status);
            assertThat(call("GET", PREFIX + server.reportId, token, null).statusCode()).isEqualTo(401);
        } finally { server.jdbc.update("UPDATE users SET status='ACTIVE' WHERE id=1"); }
        assertThat(call("GET", PREFIX + server.reportId, token, null).statusCode()).isEqualTo(200);
    }

    @Test void defaultJsonContainsNoConversationBodies() throws Exception {
        for (String path : List.of(PREFIX + server.reportId, "/api/v1/admin/chat-rooms", "/api/v1/admin/chat-rooms/" + server.roomId)) {
            var response = call("GET", path, token, null);
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).doesNotContain("삭제 원문", "alert(", "lastMessage\"", "\"content\":\"", "\"pushToken\"");
        }
    }

    @Test void revokedAdminRoleInvalidatesExistingJwt() throws Exception {
        try {
            server.jdbc.update("UPDATE users SET role='USER' WHERE id=1");
            assertThat(call("GET", PREFIX + server.reportId, token, null).statusCode()).isEqualTo(403);
        } finally { server.jdbc.update("UPDATE users SET role='ADMIN' WHERE id=1"); }
    }

    @Test void linkedActionSurvivesLaterReportFailureButDoesNotCloseReport() throws Exception {
        long id = newReport();
        var action = Map.of("action", "SUSPEND", "reason", "격리 제재 시험", "reportId", id);
        assertThat(call("PATCH", "/api/v1/admin/users/2/actions", token, action).statusCode()).isEqualTo(400);
        try {
            assertThat(call("PATCH", "/api/v1/admin/users/3/actions", token, action).statusCode()).isEqualTo(200);
            assertThat(server.jdbc.queryForObject("SELECT status FROM users WHERE id=3", String.class)).isEqualTo("SUSPENDED");
            JsonNode detail = json.readTree(call("GET", PREFIX + id, token, null).body()).path("data");
            assertThat(detail.path("actions").size()).isEqualTo(1);
            assertThat(detail.path("actions").get(0).path("action").asText()).isEqualTo("SUSPEND");
            server.jdbc.execute("ALTER TABLE admin_audit_logs ADD CONSTRAINT reject_followup_fixture CHECK (action <> 'REPORT_STATUS_UPDATED' OR target_id <> '" + id + "')");
            try {
                assertThat(call("PATCH", PREFIX + id, token, update(0, "RESOLVED", "내부", "외부")).statusCode()).isEqualTo(500);
                assertThat(server.jdbc.queryForObject("SELECT status FROM users WHERE id=3", String.class)).isEqualTo("SUSPENDED");
                assertThat(server.jdbc.queryForObject("SELECT status FROM user_reports WHERE id=?", String.class, id)).isEqualTo("OPEN");
                assertThat(server.jdbc.queryForObject("SELECT COUNT(*) FROM admin_audit_logs WHERE report_id=? AND action='USER_ACTION_APPLIED'", Integer.class, id)).isEqualTo(1);
            } finally { server.jdbc.execute("ALTER TABLE admin_audit_logs DROP CHECK reject_followup_fixture"); }
        } finally {
            server.jdbc.update("UPDATE users SET status='ACTIVE',suspended_until=NULL WHERE id=3");
        }
    }

    @Test void closurePersistsSeparateMemoReplyAndOnlyReplyEntersOutbox() throws Exception {
        long id = newReport();
        String memo = "internal-only-mySQL-메모";
        var result = call("PATCH", PREFIX + id, token, update(0, "RESOLVED", memo, "제재 없이 종결했습니다."));
        assertThat(result.statusCode()).isEqualTo(200);
        JsonNode data = json.readTree(result.body()).path("data");
        assertThat(data.path("version").asLong()).isEqualTo(1);
        assertThat(data.path("internalMemo").asText()).isEqualTo(memo);
        assertThat(server.jdbc.queryForObject("SELECT status FROM users WHERE id=3", String.class)).isEqualTo("ACTIVE");
        assertThat(server.jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE dedupe_key=?", Integer.class, "admin-report:" + id + ":version:0")).isEqualTo(1);
        var external = server.jdbc.queryForObject("SELECT CONCAT(n.body,n.payload_json,o.body,o.data_json) FROM notifications n JOIN notification_outbox o ON o.notification_id=n.id WHERE n.dedupe_key=?", String.class, "admin-report:" + id + ":version:0");
        assertThat(external).contains("제재 없이 종결했습니다.").doesNotContain(memo);
        assertThat(call("PATCH", PREFIX + id, token, update(0, "RESOLVED", memo, "제재 없이 종결했습니다.")).statusCode()).isEqualTo(409);
        assertThat(server.jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE dedupe_key=?", Integer.class, "admin-report:" + id + ":version:0")).isEqualTo(1);
    }

    @Test void simultaneousSameVersionHasExactlyOneWinner() throws Exception {
        long id = newReport();
        var gate = new CyclicBarrier(2);
        var pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> operation = () -> { gate.await(5, TimeUnit.SECONDS); return call("PATCH", PREFIX + id, token,
                    update(0, "IN_REVIEW", "동시 요청", null)).statusCode(); };
            Future<Integer> a = pool.submit(operation), b = pool.submit(operation);
            assertThat(List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS))).containsExactlyInAnyOrder(200, 409);
            assertThat(server.jdbc.queryForObject("SELECT version FROM user_reports WHERE id=?", Long.class, id)).isEqualTo(1);
            assertThat(server.jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE dedupe_key=?", Integer.class, "admin-report:" + id + ":version:0")).isEqualTo(1);
        } finally { pool.shutdownNow(); }
    }

    @Test void auditFailureRollsBackReportNotificationAndOutbox() throws Exception {
        long id = newReport();
        server.jdbc.execute("ALTER TABLE admin_audit_logs ADD CONSTRAINT reject_report_fixture CHECK (action <> 'REPORT_STATUS_UPDATED' OR target_id <> '" + id + "')");
        try {
            var response = call("PATCH", PREFIX + id, token, update(0, "RESOLVED", "비공개", "가상 답변"));
            assertThat(response.statusCode()).isEqualTo(500);
            assertThat(server.jdbc.queryForObject("SELECT version FROM user_reports WHERE id=?", Long.class, id)).isZero();
            assertThat(server.jdbc.queryForObject("SELECT status FROM user_reports WHERE id=?", String.class, id)).isEqualTo("OPEN");
            assertThat(server.jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE dedupe_key=?", Integer.class, "admin-report:" + id + ":version:0")).isZero();
            assertThat(server.jdbc.queryForObject("SELECT COUNT(*) FROM notification_outbox o LEFT JOIN notifications n ON n.id=o.notification_id WHERE n.id IS NULL", Integer.class)).isZero();
        } finally { server.jdbc.execute("ALTER TABLE admin_audit_logs DROP CHECK reject_report_fixture"); }
    }

    @Test void inspectionChecksScopeAndDoesNotModifyConversation() throws Exception {
        var before = conversationState();
        assertThat(call("POST", PREFIX + server.reportId + "/evidence-inspections", token, inspection(server.otherRoomId, 0, 50)).statusCode()).isEqualTo(403);
        var result = call("POST", PREFIX + server.reportId + "/evidence-inspections", token, inspection(server.roomId, 0, 2));
        assertThat(result.statusCode()).isEqualTo(200);
        // Raw text returned only through explicit, audited inspection. UI must render it as text.
        String nextPage = call("POST", PREFIX + server.reportId + "/evidence-inspections", token, inspection(server.roomId, 1, 2)).body();
        assertThat(result.body() + nextPage).contains("삭제 원문", "\"deleted\":true");
        assertThat(conversationState()).isEqualTo(before);
        assertThat(server.jdbc.queryForObject("SELECT COUNT(*) FROM admin_audit_logs WHERE action='CHAT_MESSAGES_INSPECTED'", Integer.class)).isGreaterThanOrEqualTo(2);
        assertThat(server.jdbc.queryForList("SELECT metadata_json FROM admin_audit_logs WHERE action='CHAT_MESSAGES_INSPECTED'", String.class).toString()).doesNotContain("삭제 원문", "alert(");
    }

    @Test void auditFailureReturnsNoMessageBody() throws Exception {
        server.jdbc.execute("CREATE TRIGGER reject_inspection_fixture BEFORE INSERT ON admin_audit_logs FOR EACH ROW BEGIN IF NEW.action = 'CHAT_MESSAGES_INSPECTED' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='synthetic audit failure'; END IF; END");
        try {
            var result = call("POST", PREFIX + server.reportId + "/evidence-inspections", token, inspection(server.roomId, 0, 50));
            assertThat(result.statusCode()).isEqualTo(500);
            assertThat(result.body()).doesNotContain("삭제 원문", "alert(", "추가 가상");
        } finally { server.jdbc.execute("DROP TRIGGER reject_inspection_fixture"); }
    }

    @Test void invalidInputCannotWrite() throws Exception {
        long id = newReport();
        assertThat(call("PATCH", PREFIX + id, token, Map.of("status", "RESOLVED", "reason", "old API")).statusCode()).isEqualTo(400);
        assertThat(call("PATCH", PREFIX + id, token, update(0, "RESOLVED", "memo", null)).statusCode()).isEqualTo(400);
        assertThat(call("PATCH", PREFIX + id, token, update(0, "OPEN", "가".repeat(1001), null)).statusCode()).isEqualTo(400);
        assertThat(call("POST", PREFIX + server.reportId + "/evidence-inspections", token, inspection(server.roomId, 0, 101)).statusCode()).isEqualTo(400);
        assertThat(server.jdbc.queryForObject("SELECT version FROM user_reports WHERE id=?", Long.class, id)).isZero();
    }

    @Test void releaseSchemaIsMigratedWithoutHibernateUpdate() {
        assertThat(server.jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='maintenance_settings' AND column_name='version' AND is_nullable='NO'", Integer.class)).isEqualTo(1);
        assertThat(server.jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='admin_ticket_adjustments' AND index_name='uk_admin_ticket_adjustment_operation' AND non_unique=0", Integer.class)).isEqualTo(1);
        assertThat(server.jdbc.queryForObject("SELECT engine FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='admin_ticket_adjustments'", String.class)).isEqualTo("InnoDB");
        assertThat(server.jdbc.queryForObject("SELECT column_type FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='ticket_ledger' AND column_name='ref_type'", String.class)).isEqualTo("varchar(30)");
    }

    @Test void maintenanceStaleContentAndConcurrentStateChangesCannotOverwrite() throws Exception {
        String path = "/api/v1/admin/maintenance";
        long version = server.jdbc.queryForObject("SELECT version FROM maintenance_settings WHERE id=1", Long.class);
        try {
            assertThat(call("PATCH", path + "/state", token, Map.of("expectedVersion", version, "enabled", true)).statusCode()).isEqualTo(200);
            assertThat(call("PATCH", path + "/content", token, Map.of("expectedVersion", version, "title", "stale", "message", "stale")).statusCode()).isEqualTo(409);
            assertThat(server.jdbc.queryForObject("SELECT enabled FROM maintenance_settings WHERE id=1", Boolean.class)).isTrue();
            long current = server.jdbc.queryForObject("SELECT version FROM maintenance_settings WHERE id=1", Long.class);
            assertThat(call("PATCH", path + "/content", token, Map.of("expectedVersion", current, "title", "updated fixture", "message", "content only")).statusCode()).isEqualTo(200);
            assertThat(server.jdbc.queryForObject("SELECT enabled FROM maintenance_settings WHERE id=1", Boolean.class)).isTrue();
            long sharedVersion = server.jdbc.queryForObject("SELECT version FROM maintenance_settings WHERE id=1", Long.class);
            var barrier = new CyclicBarrier(2);
            var pool = Executors.newFixedThreadPool(2);
            try {
                var a = pool.submit(() -> { barrier.await(); return call("PATCH", path + "/state", token, Map.of("expectedVersion", sharedVersion, "enabled", false)).statusCode(); });
                var b = pool.submit(() -> { barrier.await(); return call("PATCH", path + "/state", token, Map.of("expectedVersion", sharedVersion, "enabled", false)).statusCode(); });
                assertThat(List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS))).containsExactlyInAnyOrder(200, 409);
            } finally { pool.shutdownNow(); }
        } finally { server.jdbc.update("UPDATE maintenance_settings SET enabled=false WHERE id=1"); }
    }

    @Test void ticketReplayAndResultLookupChangeBalanceExactlyOnce() throws Exception {
        String id = UUID.randomUUID().toString();
        String path = "/api/v1/admin/tickets/adjustments";
        int before = server.jdbc.queryForObject("SELECT tickets FROM users WHERE id=3", Integer.class);
        var body = Map.of("operationId", id, "userId", 3, "amount", 3, "reason", "isolated release fixture");
        assertThat(call("POST", path, token, body).statusCode()).isEqualTo(200);
        assertThat(call("POST", path, token, body).statusCode()).isEqualTo(200);
        var receipt = call("GET", path + "/" + id, token, null);
        assertThat(receipt.statusCode()).isEqualTo(200);
        assertThat(json.readTree(receipt.body()).path("data").path("operationId").asText()).isEqualTo(id);
        assertThat(server.jdbc.queryForObject("SELECT tickets FROM users WHERE id=3", Integer.class)).isEqualTo(before + 3);
        assertThat(server.jdbc.queryForObject("SELECT COUNT(*) FROM ticket_ledger WHERE ref_id=?", Integer.class, "admin-adjustment:" + id)).isEqualTo(1);
        assertThat(call("POST", path, token, Map.of("operationId", id, "userId", 3, "amount", 4, "reason", "different")).statusCode()).isEqualTo(409);
        assertThat(call("GET", path + "/" + id, null, null).statusCode()).isEqualTo(401);
        String ordinary = server.context.getBean(JwtProvider.class).createAccessToken(IsolatedReportMySqlServer.REPORTER);
        assertThat(call("GET", path + "/" + id, ordinary, null).statusCode()).isEqualTo(403);
    }

    @Test void simultaneousTicketReplayHasOneReceiptAndOneBalanceChange() throws Exception {
        String id = UUID.randomUUID().toString();
        int before = server.jdbc.queryForObject("SELECT tickets FROM users WHERE id=3", Integer.class);
        var body = Map.of("operationId", id, "userId", 3, "amount", 2, "reason", "concurrent fixture");
        var barrier = new CyclicBarrier(2);
        var pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> request = () -> { barrier.await(); return call("POST", "/api/v1/admin/tickets/adjustments", token, body).statusCode(); };
            var a = pool.submit(request); var b = pool.submit(request);
            assertThat(List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS))).containsExactly(200, 200);
            assertThat(server.jdbc.queryForObject("SELECT tickets FROM users WHERE id=3", Integer.class)).isEqualTo(before + 2);
            assertThat(server.jdbc.queryForObject("SELECT COUNT(*) FROM admin_ticket_adjustments WHERE operation_id=?", Integer.class, id)).isEqualTo(1);
            assertThat(server.jdbc.queryForObject("SELECT COUNT(*) FROM ticket_ledger WHERE ref_id=?", Integer.class, "admin-adjustment:" + id)).isEqualTo(1);
        } finally { pool.shutdownNow(); }
    }

    @Test void ticketHistoryFailureRollsBackReceiptAndBalance() throws Exception {
        String id = UUID.randomUUID().toString();
        int before = server.jdbc.queryForObject("SELECT tickets FROM users WHERE id=3", Integer.class);
        server.jdbc.execute("CREATE TRIGGER release_ticket_failure BEFORE INSERT ON ticket_ledger FOR EACH ROW BEGIN IF NEW.ref_id='admin-adjustment:" + id + "' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='isolated history failure'; END IF; END");
        try {
            assertThat(call("POST", "/api/v1/admin/tickets/adjustments", token, Map.of("operationId", id, "userId", 3, "amount", 2, "reason", "rollback fixture")).statusCode()).isEqualTo(500);
            assertThat(server.jdbc.queryForObject("SELECT tickets FROM users WHERE id=3", Integer.class)).isEqualTo(before);
            assertThat(server.jdbc.queryForObject("SELECT COUNT(*) FROM admin_ticket_adjustments WHERE operation_id=?", Integer.class, id)).isZero();
            assertThat(server.jdbc.queryForObject("SELECT COUNT(*) FROM ticket_ledger WHERE ref_id=?", Integer.class, "admin-adjustment:" + id)).isZero();
        } finally { server.jdbc.execute("DROP TRIGGER release_ticket_failure"); }
    }

    @Test void deployedScreenRoutesExistAndEmptyQueuesAreNot404() throws Exception {
        for (int size : List.of(2, 3)) {
            var response = call("GET", "/api/v1/admin/group-matching/teams/waiting?teamSize=" + size, token, null);
            assertThat(response.statusCode()).as("Synthetic waiting response: %s", response.body()).isEqualTo(200);
            assertThat(json.readTree(response.body()).path("data").path("male").path("totalElements").asInt()).isZero();
        }
        var body = new LinkedHashMap<>(inspection(server.roomId, 0, 50));
        // The room is a path variable here; only report evidence requests carry roomId in JSON.
        body.remove("roomId");
        var response = call("POST", "/api/v1/admin/chat-rooms/" + server.roomId + "/message-inspections", token, body);
        assertThat(response.statusCode()).as("Synthetic inspection response: %s", response.body()).isEqualTo(200);
    }

    private long newReport() {
        return server.tx(() -> server.context.getBean(UserReportRepository.class).saveAndFlush(UserReport.createReceived(2L,3L,
                ReportReasonCode.HARASSMENT,"격리 HTTP 신고",ReportSourceType.PROFILE,"3")).getId());
    }
    private Map<String,Object> update(long version, String status, String memo, String reply) {
        var body = new LinkedHashMap<String,Object>();
        body.put("expectedVersion",version); body.put("status",status); body.put("internalMemo",memo); body.put("reporterReply",reply);
        return body;
    }
    private Map<String,Object> inspection(long room, int page, int size) {
        return Map.of("roomId",room,"reason","REPORT_REVIEW","from",LocalDateTime.now(ZoneOffset.UTC).minusDays(1).toString(),
                "to",LocalDateTime.now(ZoneOffset.UTC).plusMinutes(1).toString(),"page",page,"size",size);
    }
    private Map<String,List<Map<String,Object>>> conversationState() {
        var state = new LinkedHashMap<String,List<Map<String,Object>>>();
        for (String table : List.of("chat_rooms","chat_room_members","chat_messages"))
            state.put(table, server.jdbc.queryForList("SELECT * FROM " + table + " ORDER BY id"));
        return state;
    }
    private HttpResponse<String> call(String method, String path, String credential, Object body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:18089" + path)).timeout(Duration.ofSeconds(10));
        if (credential != null) request.header("Authorization", "Bearer " + credential);
        if (body != null) request.header("Content-Type", "application/json");
        request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
