-- 학과 랭킹은 이 테이블의 최초 시작 시각 이후 매칭 요청만 집계한다.
-- 기존 매칭/채팅 데이터는 삭제하지 않으며, 최초 애플리케이션 기동 시 단일 행이 생성된다.
CREATE TABLE IF NOT EXISTS department_ranking_baseline (
    id BIGINT NOT NULL,
    started_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
