-- Creates the canonical department catalog table when Hibernate ddl-auto is disabled.
-- The application seeds 63 departments from
-- classpath:departments/hanseo-departments.tsv at startup.

CREATE TABLE IF NOT EXISTS departments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(40) NOT NULL,
    name VARCHAR(100) NOT NULL,
    college_name VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL,
    display_order INT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_departments_code UNIQUE (code),
    CONSTRAINT uk_departments_name UNIQUE (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

UPDATE users SET dept_name = '문화유산보존학과' WHERE dept_name = '문화재보존학과';
UPDATE users SET dept_name = '뮤직프로덕션학과' WHERE dept_name = '실용음악과';
UPDATE users SET dept_name = '디지털산업디자인학과' WHERE dept_name = '산업디자인학과';
