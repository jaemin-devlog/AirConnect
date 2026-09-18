-- ID 1은 앱의 학과 랭킹, ID 2는 관리자 학과별 매칭 순위의 시작 시각이다.
-- 기존 매칭/채팅 데이터는 삭제하지 않으며, 각 기능이 처음 배포된 시점에 행이 생성된다.
CREATE TABLE IF NOT EXISTS department_ranking_baseline (
    id BIGINT NOT NULL,
    started_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
