-- ID 70~109 더미 데이터 생성
-- 남성 20명: 70~89, 여성 20명: 90~109
-- 재실행해도 중복 삽입되지 않도록 NOT EXISTS 조건 포함

-- 0) 사전 확인: 이미 존재하는 ID 확인
SELECT id, social_id, email
FROM users
WHERE id BETWEEN 70 AND 109
ORDER BY id;

-- 1) users 40명 생성
WITH RECURSIVE seq AS (
    SELECT 70 AS n
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < 109
)
INSERT INTO users (
    id,
    provider,
    social_id,
    email,
    name,
    nickname,
    dept_name,
    student_num,
    status,
    onboarding_status,
    created_at,
    tickets
)
SELECT
    s.n AS id,
    'KAKAO' AS provider,
    CASE
        WHEN s.n <= 89 THEN CONCAT('male-user-', LPAD(s.n, 3, '0'))
        ELSE CONCAT('female-user-', LPAD(s.n, 3, '0'))
    END AS social_id,
    CASE
        WHEN s.n <= 89 THEN CONCAT('male', s.n, '@example.com')
        ELSE CONCAT('female', s.n, '@example.com')
    END AS email,
    CASE
        WHEN s.n <= 89 THEN CONCAT('Male', s.n)
        ELSE CONCAT('Female', s.n)
    END AS name,
    CASE
        WHEN s.n <= 89 THEN CONCAT('m', s.n)
        ELSE CONCAT('f', s.n)
    END AS nickname,
    CASE MOD(s.n, 4)
        WHEN 0 THEN '컴퓨터공학과'
        WHEN 1 THEN '소프트웨어학과'
        WHEN 2 THEN '정보통신학과'
        ELSE '데이터사이언스학과'
    END AS dept_name,
    20240000 + s.n AS student_num,
    'ACTIVE' AS status,
    'FULL' AS onboarding_status,
    NOW() AS created_at,
    100 AS tickets
FROM seq s
WHERE NOT EXISTS (
    SELECT 1
    FROM users u
    WHERE u.id = s.n
);

-- 2) user_profiles 40명 생성 (users.id와 1:1 매핑)
WITH RECURSIVE seq AS (
    SELECT 70 AS n
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < 109
)
INSERT INTO user_profiles (
    user_id,
    height,
    mbti,
    smoking,
    gender,
    military,
    religion,
    residence,
    intro,
    instagram,
    updated_at
)
SELECT
    s.n AS user_id,
    CASE
        WHEN s.n <= 89 THEN 172 + MOD(s.n, 13)
        ELSE 158 + MOD(s.n, 12)
    END AS height,
    CASE MOD(s.n, 8)
        WHEN 0 THEN 'INTJ'
        WHEN 1 THEN 'ISTP'
        WHEN 2 THEN 'ENFP'
        WHEN 3 THEN 'ESTJ'
        WHEN 4 THEN 'INFP'
        WHEN 5 THEN 'INTP'
        WHEN 6 THEN 'ENFJ'
        ELSE 'ISFJ'
    END AS mbti,
    'NO' AS smoking,
    CASE
        WHEN s.n <= 89 THEN 'MALE'
        ELSE 'FEMALE'
    END AS gender,
    'NOT_APPLICABLE' AS military,
    CASE MOD(s.n, 4)
        WHEN 0 THEN 'NONE'
        WHEN 1 THEN 'CHRISTIAN'
        WHEN 2 THEN 'CATHOLIC'
        ELSE 'BUDDHIST'
    END AS religion,
    CASE MOD(s.n, 6)
        WHEN 0 THEN '서울 강남구'
        WHEN 1 THEN '서울 마포구'
        WHEN 2 THEN '서울 송파구'
        WHEN 3 THEN '경기 성남시'
        WHEN 4 THEN '경기 수원시'
        ELSE '인천 연수구'
    END AS residence,
    CASE
        WHEN s.n <= 89 THEN CONCAT('안녕하세요. 매칭 테스트 남성 더미 사용자 ', s.n, '입니다.')
        ELSE CONCAT('안녕하세요. 매칭 테스트 여성 더미 사용자 ', s.n, '입니다.')
    END AS intro,
    CASE
        WHEN s.n <= 89 THEN CONCAT('male_', s.n)
        ELSE CONCAT('female_', s.n)
    END AS instagram,
    NOW() AS updated_at
FROM seq s
WHERE EXISTS (
    SELECT 1
    FROM users u
    WHERE u.id = s.n
)
AND NOT EXISTS (
    SELECT 1
    FROM user_profiles up
    WHERE up.user_id = s.n
);

-- 3) 결과 확인 (70~109 범위)
SELECT
    (SELECT COUNT(*) FROM users WHERE id BETWEEN 70 AND 109) AS users_70_109,
    (SELECT COUNT(*) FROM user_profiles WHERE user_id BETWEEN 70 AND 109 AND gender = 'MALE') AS male_profiles_70_89,
    (SELECT COUNT(*) FROM user_profiles WHERE user_id BETWEEN 70 AND 109 AND gender = 'FEMALE') AS female_profiles_90_109;

