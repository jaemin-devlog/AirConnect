package univ.airconnect.admin.insights;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import univ.airconnect.global.error.BusinessException;
import static org.assertj.core.api.Assertions.*;

class AdminInsightsServiceTest {
    JdbcTemplate db;
    AdminInsightsService service;
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-12T03:00:00Z"), ZoneOffset.UTC);
    static final LocalDate FROM = LocalDate.parse("2026-09-01"), TO = LocalDate.parse("2026-09-10");
    TimeZone previous;

    @BeforeEach void setUp() {
        previous = TimeZone.getDefault(); TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        var ds = new DriverManagerDataSource("jdbc:h2:mem:insights-"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=DAY,VALUE,HOUR", "sa", "");
        db = new JdbcTemplate(ds);
        service = new AdminInsightsService(new NamedParameterJdbcTemplate(ds), "99", CLOCK);
        db.execute("CREATE TABLE users(id BIGINT PRIMARY KEY, role VARCHAR(20), status VARCHAR(20), onboarding_status VARCHAR(20), dept_name VARCHAR(100), nickname VARCHAR(100), name VARCHAR(100), verified_school_email VARCHAR(255),created_at TIMESTAMP,deleted_at TIMESTAMP,last_active_at TIMESTAMP)");
        db.execute("CREATE TABLE user_profiles(user_id BIGINT PRIMARY KEY,gender VARCHAR(20),profile_image_path VARCHAR(200))");
        db.execute("CREATE TABLE analytics_events(user_id BIGINT,type VARCHAR(40),source VARCHAR(20),occurred_at TIMESTAMP)");
        db.execute("CREATE TABLE matching_connections(id BIGINT, user1_id BIGINT,user2_id BIGINT,status VARCHAR(30),chat_room_id BIGINT,connected_at TIMESTAMP,responded_at TIMESTAMP)");
        db.execute("CREATE TABLE chat_messages(id BIGINT,room_id BIGINT,sender_id BIGINT,type VARCHAR(20),is_deleted BOOLEAN,created_at TIMESTAMP,content VARCHAR(100))");
        db.execute("CREATE TABLE matching_temporary_team_rooms(id BIGINT,leader_id BIGINT,team_size VARCHAR(10),team_gender VARCHAR(10),status VARCHAR(20),queued_at TIMESTAMP)");
        db.execute("CREATE TABLE matching_final_group_chat_rooms(id BIGINT,team1_room_id BIGINT,team2_room_id BIGINT,team_size VARCHAR(10),status VARCHAR(20),created_at TIMESTAMP)");
        db.execute("CREATE TABLE iap_orders(id BIGINT,user_id BIGINT,status VARCHAR(20),environment VARCHAR(20),created_at TIMESTAMP,purchase_token VARCHAR(100))");
        db.execute("CREATE TABLE ticket_ledger(user_id BIGINT,ref_type VARCHAR(30),change_amount INT,created_at TIMESTAMP)");
        user(1,"USER","ACTIVE","FULL","공학","school@example.invalid","photo");
        user(2,"USER","ACTIVE","FULL","공학",null,"photo");
        user(3,"USER","ACTIVE","FULL","인문","school@example.invalid",null);
        user(4,"USER","ACTIVE","FULL","인문",null,null);
        user(5,"USER","DELETED","FULL",null,null,null);
        user(6,"USER","ACTIVE","BASIC",null,null,null);
        user(98,"ADMIN","ACTIVE","FULL","공학",null,null);
        user(99,"USER","ACTIVE","FULL","공학",null,null);
    }
    @AfterEach void restore() { TimeZone.setDefault(previous); db.execute("SHUTDOWN"); }
    void user(long id,String role,String status,String onboard,String dept,String email,String photo) {
        db.update("INSERT INTO users VALUES(?,?,?,?,?,?,?,?,?,?,?)",id,role,status,onboard,dept,"가상회원"+id,"테스트",email,
                LocalDateTime.parse("2026-09-01T03:00:00"),status.equals("DELETED")?LocalDateTime.parse("2026-09-03T03:00:00"):null,LocalDateTime.parse("2026-09-04T03:00:00"));
        db.update("INSERT INTO user_profiles VALUES(?,?,?)",id,id%2==0?"MALE":"FEMALE",photo);
    }
    @SuppressWarnings("unchecked") static Map<String,Object> map(Object o) { return (Map<String,Object>)o; }
    @SuppressWarnings("unchecked") static List<Map<String,Object>> list(Object o) { return (List<Map<String,Object>>)o; }
    static long n(Object o) { return ((Number)o).longValue(); }

    @Test void completeQueriesRunWithEmptyActivityAndCorrectCurrentDenominators() {
        var result=service.overview(FROM,TO);
        assertThat(n(map(result.get("snapshot")).get("current_members"))).isEqualTo(4);
        assertThat(list(result.get("readiness"))).hasSize(4).allSatisfy(r->assertThat(n(r.get("members"))).isEqualTo(1));
        assertThat(list(result.get("daily"))).hasSize(10);
        assertThat(n(map(result.get("period")).get("signups"))).isEqualTo(6);
        assertThat(map(result.get("activity")).get("engagement_status")).isEqualTo("NOT_COLLECTED");
        assertThat(result.toString()).doesNotContain("school@example", "purchase_token", "content=");
    }
    @Test void drilldownPreservesAllFiltersAndRejectsSqlInputAsData() {
        assertThat(n(service.members(FROM,TO,"photo","공학","MALE",0).get("total"))).isEqualTo(1);
        assertThat(n(service.members(FROM,TO,"photo","인문","MALE",0).get("total"))).isZero();
        assertThat(n(service.members(FROM,TO,"current","' OR 1=1 --",null,0).get("total"))).isZero();
        assertThatThrownBy(()->service.members(FROM,TO,"unsafe",null,null,0)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->service.members(FROM,TO,"current",null,null,-1)).isInstanceOf(BusinessException.class);
    }
    @Test void conversationsRequireBothActualParticipantsAndExcludeDeletedOrSystemMessages() {
        db.update("INSERT INTO matching_connections VALUES(1,1,2,'ACCEPTED',10,'2026-09-02 03:00:00','2026-09-03 03:00:00')");
        db.update("INSERT INTO chat_messages VALUES(1,10,1,'TEXT',false,'2026-09-03 04:00:00','private text')");
        db.update("INSERT INTO chat_messages VALUES(2,10,2,'TEXT',true,'2026-09-03 04:00:00','deleted text')");
        db.update("INSERT INTO chat_messages VALUES(3,10,3,'TEXT',false,'2026-09-03 04:00:00','unrelated sender')");
        db.update("INSERT INTO chat_messages VALUES(4,10,2,'ENTER',false,'2026-09-03 04:00:00','system')");
        var f=map(map(service.overview(FROM,TO).get("matching")).get("funnel"));
        assertThat(n(f.get("first_message"))).isEqualTo(1); assertThat(n(f.get("reciprocal"))).isZero();
        db.update("INSERT INTO chat_messages VALUES(5,10,2,'TEXT',false,'2026-09-03 04:01:00','reply')");
        assertThat(n(map(map(service.overview(FROM,TO).get("matching")).get("funnel")).get("reciprocal"))).isEqualTo(1);
    }
    @Test void koreanMidnightActivityAndDistinctUsersAreCountedOnce() {
        db.update("INSERT INTO analytics_events VALUES(1,'APP_HEARTBEAT','CLIENT','2026-09-01 15:00:00')");
        db.update("INSERT INTO analytics_events VALUES(1,'APP_HEARTBEAT','CLIENT','2026-09-01 15:01:00')");
        db.update("INSERT INTO analytics_events VALUES(98,'APP_HEARTBEAT','CLIENT','2026-09-01 15:01:00')");
        var r=service.overview(FROM,TO);
        assertThat(n(map(r.get("period")).get("active_members"))).isEqualTo(1);
        assertThat(n(list(r.get("daily")).get(1).get("active"))).isEqualTo(1);
        assertThat(n(list(map(r.get("activity")).get("retention")).get(0).get("returned"))).isEqualTo(1);
    }
    @Test void commerceExcludesSandboxAndNoQueriesMutateRecords() {
        db.update("INSERT INTO iap_orders VALUES(1,1,'GRANTED','SANDBOX','2026-09-03 03:00:00','never-return')");
        db.update("INSERT INTO iap_orders VALUES(2,2,'GRANTED','PRODUCTION','2026-09-03 03:00:00','never-return')");
        db.update("INSERT INTO ticket_ledger VALUES(2,'IAP_ORDER',10,'2026-09-03 03:00:00')");
        var r=service.overview(FROM,TO);
        assertThat(n(list(map(r.get("commerce")).get("orders")).get(0).get("orders"))).isEqualTo(1);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM iap_orders",Long.class)).isEqualTo(2);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM users",Long.class)).isEqualTo(8);
        assertThat(r.toString()).doesNotContain("never-return");
    }
    @Test void rangeRejectsFutureReversedAndOver90Days() {
        assertThatThrownBy(()->InsightWindow.of(TO,FROM,CLOCK)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->InsightWindow.of(FROM,LocalDate.parse("2026-09-13"),CLOCK)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->InsightWindow.of(FROM.minusDays(90),TO,CLOCK)).isInstanceOf(BusinessException.class);
        var w=InsightWindow.of(FROM,TO,CLOCK);
        assertThat(w.start()).isEqualTo(Instant.parse("2026-08-31T15:00:00Z"));
        assertThat(w.previous().end()).isEqualTo(w.start());
        assertThat(w.previous().days()).isEqualTo(w.days());
    }
    @Test void paginationAndGroupUnitsAreStable() {
        for(int i=10;i<40;i++) user(i,"USER","ACTIVE","FULL","공학",null,null);
        var first=service.members(FROM,TO,"current",null,null,0);
        var second=service.members(FROM,TO,"current",null,null,1);
        assertThat(n(first.get("total"))).isEqualTo(34);
        assertThat(list(first.get("items"))).hasSize(25);
        assertThat(list(second.get("items"))).hasSize(9);
        assertThat(list(first.get("items")).stream().map(r->r.get("id")).toList())
                .doesNotContainAnyElementsOf(list(second.get("items")).stream().map(r->r.get("id")).toList());
        db.update("INSERT INTO matching_temporary_team_rooms VALUES(1,1,'TWO','FEMALE','QUEUE_WAITING','2026-09-12 02:00:00')");
        db.update("INSERT INTO matching_temporary_team_rooms VALUES(2,2,'TWO','MALE','CLOSED',NULL)");
        db.update("INSERT INTO matching_final_group_chat_rooms VALUES(1,1,2,'TWO','ACTIVE','2026-09-04 03:00:00')");
        var groups=map(service.overview(FROM,TO).get("groups"));
        assertThat(list(groups.get("current"))).hasSize(2);
        assertThat(n(list(groups.get("results")).get(0).get("rooms"))).isEqualTo(1);
    }
}
