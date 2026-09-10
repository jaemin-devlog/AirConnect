-- 1:1 매칭 재요청을 별도 행으로 보존하고 추천/연결 요청의 멱등성 결과를 저장한다.
-- 애플리케이션 배포 전에 대상 DB에서 1회 실행한다.

CREATE TABLE IF NOT EXISTS matching_recommendation_requests (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    request_key VARCHAR(100) NOT NULL,
    same_gender_only BIT(1) NOT NULL,
    candidate_user_ids VARCHAR(100) NOT NULL,
    tickets_remaining INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_matching_recommendation_user_key UNIQUE (user_id, request_key),
    INDEX idx_matching_recommendation_created (created_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS matching_connect_requests (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    request_key VARCHAR(100) NOT NULL,
    target_user_id BIGINT NOT NULL,
    connection_id BIGINT NOT NULL,
    chat_room_id BIGINT NULL,
    already_connected BIT(1) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_matching_connect_user_key UNIQUE (user_id, request_key),
    INDEX idx_matching_connect_created (created_at)
) ENGINE=InnoDB;

-- Hibernate가 과거에 생성한 (user1_id, user2_id) UNIQUE 인덱스명은 환경마다 다를 수 있다.
-- 두 컬럼만으로 구성된 UNIQUE 인덱스를 찾아 제거한다. 재요청은 새 connection ID를 사용한다.
SET @matching_pair_unique_index = (
    SELECT index_name
    FROM (
        SELECT index_name,
               GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',') AS indexed_columns
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'matching_connections'
          AND non_unique = 0
          AND index_name <> 'PRIMARY'
        GROUP BY index_name
    ) matching_unique_indexes
    WHERE indexed_columns = 'user1_id,user2_id'
    LIMIT 1
);

SET @drop_matching_pair_unique_sql = IF(
    @matching_pair_unique_index IS NULL,
    'SELECT 1',
    CONCAT('ALTER TABLE matching_connections DROP INDEX `', @matching_pair_unique_index, '`')
);
PREPARE drop_matching_pair_unique_stmt FROM @drop_matching_pair_unique_sql;
EXECUTE drop_matching_pair_unique_stmt;
DEALLOCATE PREPARE drop_matching_pair_unique_stmt;

SET @matching_pair_index_exists = (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'matching_connections'
      AND index_name = 'idx_matching_connection_pair'
);
SET @create_matching_pair_index_sql = IF(
    @matching_pair_index_exists > 0,
    'SELECT 1',
    'CREATE INDEX idx_matching_connection_pair ON matching_connections (user1_id, user2_id, connected_at)'
);
PREPARE create_matching_pair_index_stmt FROM @create_matching_pair_index_sql;
EXECUTE create_matching_pair_index_stmt;
DEALLOCATE PREPARE create_matching_pair_index_stmt;

SET @matching_status_index_exists = (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'matching_connections'
      AND index_name = 'idx_matching_connection_status'
);
SET @create_matching_status_index_sql = IF(
    @matching_status_index_exists > 0,
    'SELECT 1',
    'CREATE INDEX idx_matching_connection_status ON matching_connections (status)'
);
PREPARE create_matching_status_index_stmt FROM @create_matching_status_index_sql;
EXECUTE create_matching_status_index_stmt;
DEALLOCATE PREPARE create_matching_status_index_stmt;
