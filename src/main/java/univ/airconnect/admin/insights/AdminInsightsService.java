package univ.airconnect.admin.insights;

import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import univ.airconnect.global.error.BusinessException;
import univ.airconnect.global.error.ErrorCode;

/** Aggregate-only reads: never select message bodies, receipts, credentials or device identifiers. */
@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 30)
public class AdminInsightsService {
    private final NamedParameterJdbcTemplate jdbc;
    private final List<Long> excluded;
    private final Clock clock;
    private static final String ELIGIBLE = "(u.role IS NULL OR u.role <> 'ADMIN') AND u.id NOT IN (:excluded)";
    private static final String CURRENT = "u.status <> 'DELETED' AND u.onboarding_status = 'FULL'";
    private static final String USERS = " FROM users u LEFT JOIN user_profiles p ON p.user_id=u.id WHERE " + ELIGIBLE;
    private static final String PHOTO = "p.profile_image_path IS NOT NULL AND TRIM(p.profile_image_path) <> ''";
    private static final String EMAIL = "u.verified_school_email IS NOT NULL AND TRIM(u.verified_school_email) <> ''";
    private static final String READY = "CASE WHEN " + PHOTO + " THEN CASE WHEN " + EMAIL
            + " THEN 'both' ELSE 'photo' END ELSE CASE WHEN " + EMAIL + " THEN 'email' ELSE 'neither' END END";
    private static final String ACTIVITY = "e.type IN ('APP_SESSION_STARTED','APP_HEARTBEAT','SCREEN_VIEWED',"
            + "'USER_LOGGED_IN','MATCH_REQUEST_SENT','MATCH_REQUEST_ACCEPTED')";

    @org.springframework.beans.factory.annotation.Autowired
    public AdminInsightsService(NamedParameterJdbcTemplate jdbc,
            @Value("${admin.insights.excluded-user-ids:}") String excludedIds) {
        this(jdbc, excludedIds, Clock.systemUTC());
    }

    AdminInsightsService(NamedParameterJdbcTemplate jdbc, String excludedIds, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.excluded = excludedIds.isBlank() ? List.of(-1L) : Arrays.stream(excludedIds.split(","))
                .map(String::trim).map(Long::valueOf).toList();
    }

    private MapSqlParameterSource params(InsightWindow w) {
        ZoneId server = ZoneId.systemDefault();
        return new MapSqlParameterSource("excluded", excluded)
                .addValue("start", LocalDateTime.ofInstant(w.start(), server))
                .addValue("end", LocalDateTime.ofInstant(w.observedEnd(), server))
                .addValue("chat_start", LocalDateTime.ofInstant(w.start(), ZoneOffset.UTC))
                .addValue("chat_end", LocalDateTime.ofInstant(w.observedEnd(), ZoneOffset.UTC))
                .addValue("as_of", LocalDateTime.ofInstant(w.asOf(), server))
                .addValue("recent1", LocalDateTime.ofInstant(w.asOf(), server).minusDays(1))
                .addValue("recent7", LocalDateTime.ofInstant(w.asOf(), server).minusDays(7))
                .addValue("recent30", LocalDateTime.ofInstant(w.asOf(), server).minusDays(30))
                .addValue("offset", 32400 - server.getRules().getOffset(w.start()).getTotalSeconds())
                .addValue("today", w.asOf().atZone(InsightWindow.KOREA).toLocalDate());
    }

    private List<Map<String, Object>> rows(String sql, MapSqlParameterSource p) {
        return jdbc.queryForList(sql, p).stream().map(row -> {
            Map<String, Object> normalized = new LinkedHashMap<>();
            row.forEach((key, value) -> normalized.put(key.toLowerCase(Locale.ROOT), value));
            return normalized;
        }).toList();
    }
    private Map<String, Object> one(String sql, MapSqlParameterSource p) { return rows(sql, p).get(0); }
    private String eligibleId(String col) {
        return col + " IN (SELECT u.id FROM users u WHERE " + ELIGIBLE + ")";
    }
    private static String day(String col) { return "CAST(TIMESTAMPADD(SECOND,:offset," + col + ") AS DATE)"; }

    public Map<String, Object> overview(LocalDate from, LocalDate to) {
        InsightWindow w = InsightWindow.of(from, to, clock);
        MapSqlParameterSource p = params(w);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("from", w.from()); result.put("to", w.to()); result.put("generated_at", w.asOf());
        result.put("timezone", "Asia/Seoul");
        result.put("previous_from", w.previous().from()); result.put("previous_to", w.previous().to());
        result.put("partial_day", w.to().equals(w.asOf().atZone(InsightWindow.KOREA).toLocalDate()));
        result.put("snapshot", one("SELECT COUNT(*) AS accounts, "
                + "COALESCE(SUM(CASE WHEN " + CURRENT + " THEN 1 ELSE 0 END),0) AS current_members, "
                + "COALESCE(SUM(CASE WHEN u.status='DELETED' THEN 1 ELSE 0 END),0) AS deleted_members, "
                + "COALESCE(SUM(CASE WHEN u.status<>'DELETED' AND u.onboarding_status<>'FULL' THEN 1 ELSE 0 END),0) AS incomplete_members"
                + USERS, p));
        result.put("period", period(p));
        result.put("previous", period(params(w.previous())));
        result.put("daily", daily(w, p));
        result.put("departments", rows("SELECT COALESCE(NULLIF(TRIM(u.dept_name),''),'미입력') AS department, "
                + "COUNT(*) AS members, SUM(CASE WHEN p.gender='MALE' THEN 1 ELSE 0 END) AS male, "
                + "SUM(CASE WHEN p.gender='FEMALE' THEN 1 ELSE 0 END) AS female, "
                + "SUM(CASE WHEN u.last_active_at>=:start AND u.last_active_at<:end THEN 1 ELSE 0 END) AS last_active_members"
                + USERS + " AND " + CURRENT + " GROUP BY COALESCE(NULLIF(TRIM(u.dept_name),''),'미입력') ORDER BY members DESC, department", p));
        result.put("readiness", rows("SELECT " + READY + " AS segment, COUNT(*) AS members" + USERS
                + " AND " + CURRENT + " GROUP BY " + READY, p));
        result.put("genders", rows("SELECT COALESCE(p.gender,'UNKNOWN') AS gender, COUNT(*) AS members"
                + USERS + " AND " + CURRENT + " GROUP BY p.gender", p));
        result.put("matching", matching(p));
        result.put("groups", groups(p));
        result.put("commerce", commerce(p));
        result.put("activity", activity(p));
        result.put("notes", List.of(
                "관리자와 설정으로 명시한 테스트 회원 제외. 결제는 PRODUCTION 주문만 집계합니다.",
                "가입·탈퇴는 현재 남아 있는 기록 기준입니다. 영구 삭제·복구 전의 과거 이력을 재구성하지 않습니다.",
                "현재 회원 구성은 조회 시점 기준입니다. 기간 필터로 과거 회원 구성을 재현하지 않습니다.",
                "활동·가입 완료 이벤트는 수집된 표본입니다. 앱 전송 누락 여부는 확인되지 않았습니다.",
                "서로 대화는 1:1 매칭 당사자 양쪽의 삭제되지 않은 TEXT/IMAGE 기록 기준이며 실제 만남을 의미하지 않습니다."));
        return result;
    }

    private Map<String, Object> period(MapSqlParameterSource p) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.putAll(one("SELECT COUNT(*) AS signups FROM users u WHERE " + ELIGIBLE + " AND u.created_at>=:start AND u.created_at<:end", p));
        r.putAll(one("SELECT COUNT(*) AS withdrawals FROM users u WHERE " + ELIGIBLE + " AND u.status='DELETED' AND u.deleted_at>=:start AND u.deleted_at<:end", p));
        r.putAll(one("SELECT COUNT(DISTINCT e.user_id) AS active_members FROM analytics_events e JOIN users u ON u.id=e.user_id WHERE "
                + ELIGIBLE + " AND " + ACTIVITY + " AND e.occurred_at>=:start AND e.occurred_at<:end", p));
        r.putAll(one("SELECT COUNT(*) AS accepted FROM matching_connections m WHERE " + matchingEligible()
                + " AND m.status='ACCEPTED' AND m.responded_at>=:start AND m.responded_at<:end", p));
        r.putAll(one("SELECT COUNT(DISTINCT c.sender_id) AS chat_members FROM chat_messages c WHERE "
                + eligibleId("c.sender_id") + " AND c.type IN ('TEXT','IMAGE') AND c.is_deleted=false AND c.created_at>=:chat_start AND c.created_at<:chat_end", p));
        return r;
    }

    private List<Map<String, Object>> daily(InsightWindow w, MapSqlParameterSource p) {
        Map<String, Map<String, Object>> days = new LinkedHashMap<>();
        for (LocalDate d = w.from(); !d.isAfter(w.to()); d = d.plusDays(1)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("day", d.toString());
            for (String key : List.of("signups", "withdrawals", "active", "completed")) row.put(key, 0L);
            days.put(d.toString(), row);
        }
        mergeDays(days, "signups", rows("SELECT " + day("u.created_at") + " AS day, COUNT(*) AS value FROM users u WHERE "
                + ELIGIBLE + " AND u.created_at>=:start AND u.created_at<:end GROUP BY 1", p));
        mergeDays(days, "withdrawals", rows("SELECT " + day("u.deleted_at") + " AS day, COUNT(*) AS value FROM users u WHERE "
                + ELIGIBLE + " AND u.status='DELETED' AND u.deleted_at>=:start AND u.deleted_at<:end GROUP BY 1", p));
        for (String type : List.of("active", "completed")) {
            String condition = type.equals("active") ? ACTIVITY : "e.type='SIGN_UP_COMPLETED'";
            mergeDays(days, type, rows("SELECT " + day("e.occurred_at") + " AS day, COUNT(DISTINCT e.user_id) AS value FROM analytics_events e JOIN users u ON u.id=e.user_id WHERE "
                    + ELIGIBLE + " AND " + condition + " AND e.occurred_at>=:start AND e.occurred_at<:end GROUP BY 1", p));
        }
        return new ArrayList<>(days.values());
    }
    private static void mergeDays(Map<String, Map<String, Object>> days, String key, List<Map<String, Object>> values) {
        values.forEach(row -> { var target = days.get(row.get("day").toString()); if (target != null) target.put(key, row.get("value")); });
    }
    private String matchingEligible() { return eligibleId("m.user1_id") + " AND " + eligibleId("m.user2_id"); }
    private String messageExists(String sender) {
        // Chat timestamps are UTC even if the JVM's default timezone is not UTC.
        return "EXISTS (SELECT 1 FROM chat_messages c WHERE c.room_id=m.chat_room_id AND c.sender_id=" + sender
                + " AND c.type IN ('TEXT','IMAGE') AND c.is_deleted=false AND c.created_at<:chat_end)";
    }
    private Map<String, Object> matching(MapSqlParameterSource p) {
        String base = " FROM matching_connections m WHERE " + matchingEligible() + " AND m.connected_at>=:start AND m.connected_at<:end";
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("statuses", rows("SELECT m.status, COUNT(*) AS count" + base + " GROUP BY m.status", p));
        r.put("funnel", one("SELECT COUNT(*) AS requests, COALESCE(SUM(CASE WHEN m.status='ACCEPTED' THEN 1 ELSE 0 END),0) AS accepted, "
                + "COALESCE(SUM(CASE WHEN m.chat_room_id IS NOT NULL THEN 1 ELSE 0 END),0) AS rooms, "
                + "COALESCE(SUM(CASE WHEN " + messageExists("m.user1_id") + " OR " + messageExists("m.user2_id") + " THEN 1 ELSE 0 END),0) AS first_message, "
                + "COALESCE(SUM(CASE WHEN " + messageExists("m.user1_id") + " AND " + messageExists("m.user2_id") + " THEN 1 ELSE 0 END),0) AS reciprocal, "
                + "AVG(CASE WHEN m.responded_at>=m.connected_at THEN TIMESTAMPDIFF(SECOND,m.connected_at,m.responded_at) END) AS response_seconds" + base, p));
        return r;
    }
    private Map<String, Object> groups(MapSqlParameterSource p) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("current", rows("SELECT t.team_size,t.team_gender,t.status,COUNT(*) AS teams, "
                + "AVG(CASE WHEN t.status='QUEUE_WAITING' AND t.queued_at<=:as_of THEN TIMESTAMPDIFF(SECOND,t.queued_at,:as_of) END) AS wait_seconds "
                + "FROM matching_temporary_team_rooms t WHERE " + eligibleId("t.leader_id") + " GROUP BY t.team_size,t.team_gender,t.status", p));
        r.put("results", rows("SELECT f.team_size, f.status, COUNT(*) AS rooms FROM matching_final_group_chat_rooms f "
                + "JOIN matching_temporary_team_rooms t1 ON t1.id=f.team1_room_id JOIN matching_temporary_team_rooms t2 ON t2.id=f.team2_room_id WHERE "
                + eligibleId("t1.leader_id") + " AND " + eligibleId("t2.leader_id")
                + " AND f.created_at>=:start AND f.created_at<:end GROUP BY f.team_size,f.status", p));
        return r;
    }
    private Map<String, Object> commerce(MapSqlParameterSource p) {
        return Map.of("orders", rows("SELECT o.status,COUNT(*) AS orders,COUNT(DISTINCT o.user_id) AS members FROM iap_orders o WHERE "
                + eligibleId("o.user_id") + " AND o.environment='PRODUCTION' AND o.created_at>=:start AND o.created_at<:end GROUP BY o.status", p),
                "tickets", rows("SELECT l.ref_type, SUM(CASE WHEN l.change_amount>0 THEN l.change_amount ELSE 0 END) AS granted, "
                + "SUM(CASE WHEN l.change_amount<0 THEN -l.change_amount ELSE 0 END) AS spent, COUNT(*) AS changes "
                + "FROM ticket_ledger l WHERE " + eligibleId("l.user_id") + " AND l.created_at>=:start AND l.created_at<:end GROUP BY l.ref_type", p),
                "revenue_status", "NOT_COLLECTED");
    }
    private Map<String, Object> activity(MapSqlParameterSource p) {
        String base = " FROM analytics_events e JOIN users u ON u.id=e.user_id WHERE " + ELIGIBLE + " AND " + ACTIVITY
                + " AND e.occurred_at>=:start AND e.occurred_at<:end";
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("recent", one("SELECT "
                + "COALESCE(SUM(CASE WHEN u.last_active_at>=:recent1 AND u.last_active_at<=:as_of THEN 1 ELSE 0 END),0) AS day1, "
                + "COALESCE(SUM(CASE WHEN u.last_active_at>=:recent7 AND u.last_active_at<=:as_of THEN 1 ELSE 0 END),0) AS day7, "
                + "COALESCE(SUM(CASE WHEN u.last_active_at>=:recent30 AND u.last_active_at<=:as_of THEN 1 ELSE 0 END),0) AS day30, "
                + "COALESCE(SUM(CASE WHEN u.last_active_at IS NULL OR u.last_active_at<:recent30 THEN 1 ELSE 0 END),0) AS inactive"
                + USERS + " AND " + CURRENT, p));
        result.put("coverage", one("SELECT COUNT(*) AS events,COUNT(DISTINCT e.user_id) AS members,MIN(e.occurred_at) AS first_event,MAX(e.occurred_at) AS last_event" + base, p));
        result.put("heatmap", rows("SELECT " + day("e.occurred_at") + " AS day, HOUR(TIMESTAMPADD(SECOND,:offset,e.occurred_at)) AS hour,COUNT(DISTINCT e.user_id) AS members"
                + base + " GROUP BY 1,2", p));
        result.put("sessions", one("SELECT COUNT(DISTINCT e.user_id) AS members,COUNT(*) AS starts FROM analytics_events e JOIN users u ON u.id=e.user_id WHERE "
                + ELIGIBLE + " AND e.source='CLIENT' AND e.type='APP_SESSION_STARTED' AND e.occurred_at>=:start AND e.occurred_at<:end", p));
        List<Map<String, Object>> retention = new ArrayList<>();
        for (int lag : List.of(1,7)) {
            String target = "TIMESTAMPADD(DAY," + lag + "," + day("u.created_at") + ")";
            Map<String, Object> item = one("SELECT COUNT(*) AS eligible, COALESCE(SUM(CASE WHEN EXISTS (SELECT 1 FROM analytics_events e WHERE e.user_id=u.id AND "
                    + ACTIVITY + " AND " + day("e.occurred_at") + "=" + target + ") THEN 1 ELSE 0 END),0) AS returned FROM users u WHERE "
                    + ELIGIBLE + " AND u.created_at>=:start AND u.created_at<:end AND " + target + "<:today", p);
            item.put("day", lag); retention.add(item);
        }
        result.put("retention", retention);
        Map<String, Map<String, Object>> cohorts = new TreeMap<>();
        for (int lag : List.of(1,7)) {
            String target = "TIMESTAMPADD(DAY," + lag + "," + day("u.created_at") + ")";
            var cohortRows = rows("SELECT " + day("u.created_at") + " AS cohort_day,COUNT(*) AS eligible, "
                    + "COALESCE(SUM(CASE WHEN EXISTS (SELECT 1 FROM analytics_events e WHERE e.user_id=u.id AND " + ACTIVITY
                    + " AND " + day("e.occurred_at") + "=" + target + ") THEN 1 ELSE 0 END),0) AS returned FROM users u WHERE "
                    + ELIGIBLE + " AND u.created_at>=:start AND u.created_at<:end AND " + target + "<:today GROUP BY 1", p);
            for(var row : cohortRows) {
                String week = LocalDate.parse(row.get("cohort_day").toString())
                        .with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString();
                var entry=cohorts.computeIfAbsent(week, key -> new LinkedHashMap<>(Map.of("week",key,"eligible1",0L,"returned1",0L,"eligible7",0L,"returned7",0L)));
                for(String key : List.of("eligible","returned")) entry.put(key+lag,
                        ((Number)entry.get(key+lag)).longValue()+((Number)row.get(key)).longValue());
            }
        }
        result.put("cohorts",new ArrayList<>(cohorts.values()));
        result.put("engagement_status", "NOT_COLLECTED");
        result.put("engagement_reason", "전면 활성 시간 수집 계약이 없습니다. 시작·종료 간격을 실제 이용 시간으로 대체하지 않습니다.");
        return result;
    }

    public Map<String, Object> members(LocalDate from, LocalDate to, String segment, String department, String gender, int page) {
        if (page < 0 || page > 10000 || (department != null && department.length()>100)
                || (gender != null && !List.of("MALE","FEMALE","UNKNOWN").contains(gender)))
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        MapSqlParameterSource p = params(InsightWindow.of(from, to, clock)).addValue("offset_rows", page*25);
        String condition = switch(segment) {
            case "current" -> CURRENT;
            case "incomplete" -> "u.status<>'DELETED' AND u.onboarding_status<>'FULL'";
            case "inactive" -> CURRENT + " AND (u.last_active_at IS NULL OR u.last_active_at<:recent30)";
            case "signups" -> "u.created_at>=:start AND u.created_at<:end";
            case "withdrawals" -> "u.status='DELETED' AND u.deleted_at>=:start AND u.deleted_at<:end";
            case "active" -> "EXISTS (SELECT 1 FROM analytics_events e WHERE e.user_id=u.id AND " + ACTIVITY + " AND e.occurred_at>=:start AND e.occurred_at<:end)";
            case "chat" -> "EXISTS (SELECT 1 FROM chat_messages c WHERE c.sender_id=u.id AND c.type IN ('TEXT','IMAGE') AND c.is_deleted=false AND c.created_at>=:chat_start AND c.created_at<:chat_end)";
            case "both", "photo", "email", "neither" -> CURRENT + " AND " + READY + "=:segment";
            default -> throw new BusinessException(ErrorCode.INVALID_REQUEST);
        };
        p.addValue("segment",segment);
        if (department != null) { condition += " AND COALESCE(NULLIF(TRIM(u.dept_name),''),'미입력')=:department"; p.addValue("department",department); }
        if (gender != null) { condition += " AND COALESCE(p.gender,'UNKNOWN')=:gender"; p.addValue("gender",gender); }
        String base = USERS + " AND (" + condition + ")";
        return Map.of("total", one("SELECT COUNT(*) AS total" + base,p).get("total"), "page",page,"size",25,
                "items",rows("SELECT u.id, u.nickname, u.name, u.dept_name AS department, u.status, u.created_at, "
                        + "COALESCE(p.gender,'UNKNOWN') AS gender," + READY + " AS readiness" + base + " ORDER BY u.created_at DESC,u.id DESC LIMIT 25 OFFSET :offset_rows",p));
    }
}
