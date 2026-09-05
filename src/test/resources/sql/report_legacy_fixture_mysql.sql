-- Synthetic baseline derived from UserReport/AdminAuditLog at 921f5879.
-- Not a production schema dump. Only the disposable verification database may run this.
DROP TABLE user_reports;
DROP TABLE admin_audit_logs;
CREATE TABLE user_reports (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 reporter_user_id BIGINT NOT NULL, reported_user_id BIGINT NOT NULL,
 reason VARCHAR(40) NOT NULL, detail VARCHAR(1000), source_type VARCHAR(30) NOT NULL,
 source_id VARCHAR(120), status VARCHAR(30) NOT NULL,
 created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
 INDEX idx_user_reports_reporter_created (reporter_user_id,created_at),
 INDEX idx_user_reports_reported_status (reported_user_id,status,created_at),
 INDEX idx_user_reports_status_created (status,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE admin_audit_logs (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, actor_user_id BIGINT,
 action VARCHAR(60) NOT NULL, target_type VARCHAR(60), target_id VARCHAR(120),
 summary VARCHAR(300) NOT NULL, reason VARCHAR(500), metadata_json JSON NOT NULL,
 api_method VARCHAR(12), api_path VARCHAR(200), http_status INT, duration_ms BIGINT,
 created_at DATETIME(6) NOT NULL,
 INDEX idx_admin_audit_actor_created (actor_user_id,created_at),
 INDEX idx_admin_audit_action_created (action,created_at),
 INDEX idx_admin_audit_target_created (target_type,target_id,created_at),
 INDEX idx_admin_audit_api_created (api_path,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO user_reports
 (reporter_user_id,reported_user_id,reason,detail,source_type,source_id,status,created_at,updated_at)
VALUES (2,3,'HARASSMENT','이전 신고 가상 자료 🛫','OTHER',NULL,'RESOLVED','2026-09-01 01:02:03.123456','2026-09-02 03:04:05.123456'),
       (2,3,'HARASSMENT','이전 접수 가상 자료','OTHER',NULL,'OPEN','2026-09-01 01:02:03.123456','2026-09-02 03:04:05.123456'),
       (2,3,'HARASSMENT','이전 기각 가상 자료','OTHER',NULL,'REJECTED','2026-09-01 01:02:03.123456','2026-09-02 03:04:05.123456');
INSERT INTO admin_audit_logs (actor_user_id,action,target_type,target_id,summary,metadata_json,created_at)
VALUES (1,'REPORT_STATUS_UPDATED','REPORT','1','이전 처리 가상 자료',JSON_OBJECT('legacy',true),'2026-09-02 03:04:05.123456');
