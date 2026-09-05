package univ.airconnect.admin;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminIntegrityQueryRepositoryTest {

    private JdbcTemplate jdbc;
    private AdminIntegrityQueryRepository repository;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:admin-integrity;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("DROP ALL OBJECTS");
        jdbc.execute("CREATE TABLE chat_rooms (id BIGINT PRIMARY KEY, name VARCHAR(100), type VARCHAR(20), user1_id BIGINT, user2_id BIGINT, updated_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE chat_room_members (id BIGINT PRIMARY KEY, chat_room_id BIGINT, hidden_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE matching_connections (id BIGINT PRIMARY KEY, user1_id BIGINT, user2_id BIGINT, chat_room_id BIGINT, status VARCHAR(20), connected_at TIMESTAMP, responded_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE notifications (id BIGINT PRIMARY KEY)");
        jdbc.execute("CREATE TABLE notification_outbox (id BIGINT PRIMARY KEY, notification_id BIGINT, user_id BIGINT, status VARCHAR(20), updated_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE ticket_ledger (id BIGINT PRIMARY KEY, user_id BIGINT, change_amount INT, before_amount INT, after_amount INT, ref_type VARCHAR(30), ref_id VARCHAR(120), created_at TIMESTAMP)");
        repository = new AdminIntegrityQueryRepository(jdbc);
    }

    @Test
    void exposesConcreteTargetsWithoutTreatingVisibleRoomsAsBroken() {
        jdbc.update("INSERT INTO chat_rooms VALUES (1,'ended','GROUP',NULL,NULL,CURRENT_TIMESTAMP), (2,'active','PERSONAL',10,11,CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO chat_room_members VALUES (1,1,CURRENT_TIMESTAMP), (2,1,CURRENT_TIMESTAMP), (3,2,NULL), (4,2,NULL)");
        jdbc.update("INSERT INTO matching_connections VALUES (3,10,11,NULL,'ACCEPTED',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP), (4,12,13,999,'ACCEPTED',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO notification_outbox VALUES (5,500,10,'FAILED',CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO ticket_ledger VALUES (6,10,1,3,9,'ADMIN_ADJUSTMENT','broken',CURRENT_TIMESTAMP), (7,10,1,3,4,'IAP_ORDER','same',CURRENT_TIMESTAMP), (8,10,1,4,5,'IAP_ORDER','same',CURRENT_TIMESTAMP)");

        var rooms = repository.find("chat_rooms_without_members", 0, 20);
        var missingRoom = repository.find("accepted_matching_missing_chat_room_row", 0, 20);
        var orphan = repository.find("outbox_missing_notification", 0, 20);
        var brokenTicket = repository.find("ticket_ledger_amount_mismatch", 0, 20);
        var duplicateTicket = repository.find("ticket_ledger_duplicate_refs", 0, 20);

        assertThat(rooms.totalElements()).isEqualTo(1);
        assertThat(rooms.items().get(0).roomId()).isEqualTo(1L);
        assertThat(rooms.items().get(0).detail()).contains("전체 참여 기록 2명", "표시 중 0명");
        assertThat(missingRoom.items().get(0).matchingId()).isEqualTo(4L);
        assertThat(orphan.items().get(0).outboxId()).isEqualTo(5L);
        assertThat(brokenTicket.items()).extracting(AdminDtos.IntegrityIssueItem::ticketHistoryId).containsExactly(6L);
        assertThat(duplicateTicket.items().get(0).detail()).contains("2건");
    }

    @Test
    void rejectsUnknownCheckKeyBeforeBuildingSql() {
        assertThatThrownBy(() -> repository.find("unknown", 0, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("지원하지 않는");
    }
}
