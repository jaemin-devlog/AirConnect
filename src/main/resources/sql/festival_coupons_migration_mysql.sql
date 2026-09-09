-- Apply before deploying the festival coupon API when Hibernate ddl-auto is disabled.
-- The application startup seeder inserts the 500 codes from
-- classpath:festival/coupons-500.txt and safely skips codes already present.
CREATE TABLE festival_coupons (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(6) NOT NULL,
    ticket_amount INT NOT NULL,
    redeemed_by_user_id BIGINT NULL,
    redeemed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_festival_coupon_code UNIQUE (code),
    INDEX idx_festival_coupon_redeemed_by (redeemed_by_user_id)
) ENGINE=InnoDB;

-- If ticket_ledger.ref_type is a native ENUM rather than VARCHAR, add
-- FESTIVAL_COUPON while preserving every existing ENUM value. Review
-- ticket_history_reference_types_mysql.sql before changing that column.
