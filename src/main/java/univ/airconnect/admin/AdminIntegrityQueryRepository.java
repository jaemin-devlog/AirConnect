package univ.airconnect.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class AdminIntegrityQueryRepository {

    private final JdbcTemplate jdbcTemplate;

    public AdminDtos.PageResponse<AdminDtos.IntegrityIssueItem> find(String key, int page, int size) {
        QueryDefinition definition = definition(key);
        long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM (" + definition.baseSql() + ") integrity_rows",
                Long.class
        );
        List<AdminDtos.IntegrityIssueItem> items = jdbcTemplate.queryForList(
                        definition.baseSql() + " ORDER BY " + definition.orderBy() + " LIMIT ? OFFSET ?",
                        size,
                        page * size
                ).stream()
                .map(row -> toItem(key, row))
                .toList();
        int totalPages = total == 0 ? 0 : (int) Math.ceil(total / (double) size);
        return new AdminDtos.PageResponse<>(items, page, size, total, totalPages, page + 1 < totalPages);
    }

    private QueryDefinition definition(String key) {
        return switch (key) {
            case "chat_rooms_without_members" -> new QueryDefinition("""
                    SELECT cr.id AS item_id, cr.id AS room_id, cr.name AS title, cr.type AS item_status,
                           cr.user1_id AS user_id, cr.user2_id AS related_user_id,
                           COUNT(crm.id) AS total_count,
                           SUM(CASE WHEN crm.id IS NOT NULL AND crm.hidden_at IS NULL THEN 1 ELSE 0 END) AS visible_count,
                           SUM(CASE WHEN crm.hidden_at IS NOT NULL THEN 1 ELSE 0 END) AS hidden_count,
                           cr.updated_at AS occurred_at
                    FROM chat_rooms cr
                    LEFT JOIN chat_room_members crm ON crm.chat_room_id = cr.id
                    GROUP BY cr.id, cr.name, cr.type, cr.user1_id, cr.user2_id, cr.updated_at
                    HAVING SUM(CASE WHEN crm.id IS NOT NULL AND crm.hidden_at IS NULL THEN 1 ELSE 0 END) = 0
                    """, "occurred_at DESC, item_id DESC");
            case "personal_rooms_invalid_member_count" -> new QueryDefinition("""
                    SELECT cr.id AS item_id, cr.id AS room_id, cr.name AS title, cr.type AS item_status,
                           cr.user1_id AS user_id, cr.user2_id AS related_user_id,
                           COUNT(crm.id) AS total_count,
                           SUM(CASE WHEN crm.id IS NOT NULL AND crm.hidden_at IS NULL THEN 1 ELSE 0 END) AS visible_count,
                           SUM(CASE WHEN crm.hidden_at IS NOT NULL THEN 1 ELSE 0 END) AS hidden_count,
                           cr.updated_at AS occurred_at
                    FROM chat_rooms cr
                    LEFT JOIN chat_room_members crm ON crm.chat_room_id = cr.id
                    WHERE cr.type = 'PERSONAL'
                    GROUP BY cr.id, cr.name, cr.type, cr.user1_id, cr.user2_id, cr.updated_at
                    HAVING SUM(CASE WHEN crm.id IS NOT NULL AND crm.hidden_at IS NULL THEN 1 ELSE 0 END) <> 2
                    """, "occurred_at DESC, item_id DESC");
            case "accepted_matching_without_chat_room" -> new QueryDefinition("""
                    SELECT mc.id AS item_id, mc.id AS matching_id, '1:1 매칭' AS title, mc.status AS item_status,
                           mc.user1_id AS user_id, mc.user2_id AS related_user_id, mc.chat_room_id AS room_id,
                           0 AS total_count, 0 AS visible_count, 0 AS hidden_count,
                           COALESCE(mc.responded_at, mc.connected_at) AS occurred_at
                    FROM matching_connections mc
                    WHERE mc.status = 'ACCEPTED' AND mc.chat_room_id IS NULL
                    """, "occurred_at DESC, item_id DESC");
            case "accepted_matching_missing_chat_room_row" -> new QueryDefinition("""
                    SELECT mc.id AS item_id, mc.id AS matching_id, '1:1 매칭' AS title, mc.status AS item_status,
                           mc.user1_id AS user_id, mc.user2_id AS related_user_id, mc.chat_room_id AS room_id,
                           0 AS total_count, 0 AS visible_count, 0 AS hidden_count,
                           COALESCE(mc.responded_at, mc.connected_at) AS occurred_at
                    FROM matching_connections mc
                    LEFT JOIN chat_rooms cr ON cr.id = mc.chat_room_id
                    WHERE mc.status = 'ACCEPTED' AND mc.chat_room_id IS NOT NULL AND cr.id IS NULL
                    """, "occurred_at DESC, item_id DESC");
            case "outbox_missing_notification" -> new QueryDefinition("""
                    SELECT no.id AS item_id, no.id AS outbox_id, no.notification_id AS notification_id,
                           '알림 전송 작업' AS title, no.status AS item_status, no.user_id AS user_id,
                           0 AS total_count, 0 AS visible_count, 0 AS hidden_count,
                           no.updated_at AS occurred_at
                    FROM notification_outbox no
                    LEFT JOIN notifications n ON n.id = no.notification_id
                    WHERE n.id IS NULL
                    """, "occurred_at DESC, item_id DESC");
            case "ticket_ledger_amount_mismatch" -> new QueryDefinition("""
                    SELECT tl.id AS item_id, tl.id AS ticket_history_id, '티켓 변경 내역' AS title,
                           tl.ref_type AS item_status, tl.user_id AS user_id,
                           tl.before_amount AS before_amount, tl.change_amount AS change_amount,
                           tl.after_amount AS after_amount, tl.ref_id AS reference_value,
                           0 AS total_count, 0 AS visible_count, 0 AS hidden_count,
                           tl.created_at AS occurred_at
                    FROM ticket_ledger tl
                    WHERE tl.before_amount + tl.change_amount <> tl.after_amount
                    """, "occurred_at DESC, item_id DESC");
            case "ticket_ledger_duplicate_refs" -> new QueryDefinition("""
                    SELECT MIN(tl.id) AS item_id, MIN(tl.user_id) AS user_id, '중복 연결 정보' AS title,
                           tl.ref_type AS item_status, tl.ref_id AS reference_value,
                           COUNT(*) AS total_count, 0 AS visible_count, 0 AS hidden_count,
                           MAX(tl.created_at) AS occurred_at
                    FROM ticket_ledger tl
                    GROUP BY tl.ref_type, tl.ref_id
                    HAVING COUNT(*) > 1
                    """, "occurred_at DESC, item_id DESC");
            default -> throw new IllegalArgumentException("지원하지 않는 데이터 점검 항목입니다.");
        };
    }

    private AdminDtos.IntegrityIssueItem toItem(String key, Map<String, Object> row) {
        Long itemId = longValue(row.get("item_id"));
        long total = longValue(row.get("total_count"), 0L);
        long visible = longValue(row.get("visible_count"), 0L);
        long hidden = longValue(row.get("hidden_count"), 0L);
        String detail = switch (key) {
            case "chat_rooms_without_members", "personal_rooms_invalid_member_count" ->
                    "전체 참여 기록 " + total + "명 · 표시 중 " + visible + "명 · 숨김/퇴장 " + hidden + "명";
            case "accepted_matching_without_chat_room" -> "수락됐지만 연결된 채팅방 번호가 없습니다.";
            case "accepted_matching_missing_chat_room_row" -> "기록된 채팅방 #" + longValue(row.get("room_id")) + "을 찾을 수 없습니다.";
            case "outbox_missing_notification" -> "원본 알림 #" + longValue(row.get("notification_id")) + "을 찾을 수 없습니다.";
            case "ticket_ledger_amount_mismatch" -> "변경 전 " + longValue(row.get("before_amount"), 0L)
                    + " + 증감 " + longValue(row.get("change_amount"), 0L)
                    + " ≠ 변경 후 " + longValue(row.get("after_amount"), 0L);
            case "ticket_ledger_duplicate_refs" -> "같은 연결 정보로 " + total + "건이 기록되어 있습니다.";
            default -> "";
        };
        return new AdminDtos.IntegrityIssueItem(
                key,
                String.valueOf(itemId),
                stringValue(row.get("title")),
                stringValue(row.get("item_status")),
                detail,
                longValue(row.get("user_id")),
                longValue(row.get("related_user_id")),
                longValue(row.get("room_id")),
                longValue(row.get("matching_id")),
                longValue(row.get("outbox_id")),
                longValue(row.get("notification_id")),
                longValue(row.get("ticket_history_id")),
                stringValue(row.get("reference_value")),
                timeValue(row.get("occurred_at"))
        );
    }

    private Long longValue(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private long longValue(Object value, long fallback) {
        Long converted = longValue(value);
        return converted == null ? fallback : converted;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private LocalDateTime timeValue(Object value) {
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        return value instanceof Timestamp timestamp ? timestamp.toLocalDateTime() : null;
    }

    private record QueryDefinition(String baseSql, String orderBy) {
    }
}
