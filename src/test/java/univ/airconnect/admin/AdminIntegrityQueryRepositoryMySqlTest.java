package univ.airconnect.admin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfSystemProperty(named = "airconnect.isolated.mysql", matches = "true")
class AdminIntegrityQueryRepositoryMySqlTest {

    private JdbcTemplate jdbc;
    private AdminIntegrityQueryRepository repository;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(IsolatedReportMySqlServer.isolatedDataSource());
        for (String table : new String[]{"chat_room_members", "chat_rooms", "matching_connections",
                "notification_outbox", "notifications", "ticket_ledger"}) {
            jdbc.execute("DROP TABLE IF EXISTS " + table);
        }
        jdbc.execute("CREATE TABLE chat_rooms (id BIGINT PRIMARY KEY, name VARCHAR(100), type VARCHAR(20), user1_id BIGINT, user2_id BIGINT, updated_at DATETIME(6)) ENGINE=InnoDB");
        jdbc.execute("CREATE TABLE chat_room_members (id BIGINT PRIMARY KEY, chat_room_id BIGINT, hidden_at DATETIME(6)) ENGINE=InnoDB");
        jdbc.execute("CREATE TABLE matching_connections (id BIGINT PRIMARY KEY, user1_id BIGINT, user2_id BIGINT, chat_room_id BIGINT, status VARCHAR(20), connected_at DATETIME(6), responded_at DATETIME(6)) ENGINE=InnoDB");
        jdbc.execute("CREATE TABLE notifications (id BIGINT PRIMARY KEY) ENGINE=InnoDB");
        jdbc.execute("CREATE TABLE notification_outbox (id BIGINT PRIMARY KEY, notification_id BIGINT, user_id BIGINT, status VARCHAR(20), updated_at DATETIME(6)) ENGINE=InnoDB");
        jdbc.execute("CREATE TABLE ticket_ledger (id BIGINT PRIMARY KEY, user_id BIGINT, change_amount INT, before_amount INT, after_amount INT, ref_type VARCHAR(30), ref_id VARCHAR(120), created_at DATETIME(6)) ENGINE=InnoDB");
        repository = new AdminIntegrityQueryRepository(jdbc);
    }

    @Test
    void groupedAndOrphanQueriesRunOnMySqlEight() {
        jdbc.update("INSERT INTO chat_rooms VALUES (1,'closed','GROUP',NULL,NULL,UTC_TIMESTAMP(6))");
        jdbc.update("INSERT INTO chat_room_members VALUES (1,1,UTC_TIMESTAMP(6))");
        jdbc.update("INSERT INTO notification_outbox VALUES (2,404,10,'FAILED',UTC_TIMESTAMP(6))");
        jdbc.update("INSERT INTO ticket_ledger VALUES (3,10,1,1,2,'IAP_ORDER','dup',UTC_TIMESTAMP(6)), (4,10,1,2,3,'IAP_ORDER','dup',UTC_TIMESTAMP(6))");

        assertThat(repository.find("chat_rooms_without_members", 0, 20).totalElements()).isEqualTo(1);
        assertThat(repository.find("outbox_missing_notification", 0, 20).items().get(0).outboxId()).isEqualTo(2L);
        assertThat(repository.find("ticket_ledger_duplicate_refs", 0, 20).items().get(0).detail()).contains("2건");
    }
}
