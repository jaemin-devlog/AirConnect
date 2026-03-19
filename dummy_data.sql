-- 남녀 각각 10명씩 더미데이터 생성 (총 20명)
-- 데이터베이스: airconnect

-- 1. 남성 10명 + 여성 10명 = 20명 users 데이터 삽입
INSERT INTO users (
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
) VALUES
-- 남성 10명 (ID: 1~10)
('KAKAO', 'male-user-001', 'male1@example.com', '홍길동', '길동이', '컴퓨터공학과', 20240101, 'ACTIVE', 'FULL', NOW(), 100),
('KAKAO', 'male-user-002', 'male2@example.com', '김철수', '철수', '소프트웨어학과', 20240102, 'ACTIVE', 'FULL', NOW(), 100),
('KAKAO', 'male-user-003', 'male3@example.com', '이영호', '영호', '정보통신학과', 20240103, 'ACTIVE', 'FULL', NOW(), 100),
('KAKAO', 'male-user-004', 'male4@example.com', '박준호', '준호', '컴퓨터공학과', 20240104, 'ACTIVE', 'FULL', NOW(), 100),
('KAKAO', 'male-user-005', 'male5@example.com', '최민준', '민준', '소프트웨어학과', 20240105, 'ACTIVE', 'FULL', NOW(), 100),
('KAKAO', 'male-user-006', 'male6@example.com', '정수호', '수호', '데이터사이언스학과', 20240106, 'ACTIVE', 'FULL', NOW(), 100),
('KAKAO', 'male-user-007', 'male7@example.com', '오재훈', '재훈', '정보통신학과', 20240107, 'ACTIVE', 'FULL', NOW(), 100),
('KAKAO', 'male-user-008', 'male8@example.com', '손동욱', '동욱', '컴퓨터공학과', 20240108, 'ACTIVE', 'FULL', NOW(), 100),
('KAKAO', 'male-user-009', 'male9@example.com', '류준호', '준호', '소프트웨어학과', 20240109, 'ACTIVE', 'FULL', NOW(), 100),
('KAKAO', 'male-user-010', 'male10@example.com', '신상민', '상민', '정보통신학과', 20240110, 'ACTIVE', 'FULL', NOW(), 100),

-- 여성 10명 (ID: 11~20)
('KAKAO', 'female-user-001', 'female1@example.com', '이지은', '지은', '컴퓨터공학과', 20240201, 'ACTIVE', 'FULL', NOW(), 100),
('KAKAO', 'female-user-002', 'female2@example.com', '박수진', '수진', '소프트웨어학과', 20240202, 'ACTIVE', 'FULL', NOW(), 100),
('KAKAO', 'female-user-003', 'female3@example.com', '김민지', '민지', '정보통신학과', 20240203, 'ACTIVE', 'FULL', NOW(), 100),
('KAKAO', 'female-user-004', 'female4@example.com', '정유나', '유나', '컴퓨터공학과', 20240204, 'ACTIVE', 'FULL', NOW(), 100),
('KAKAO', 'female-user-005', 'female5@example.com', '이서연', '서연', '소프트웨어학과', 20240205, 'ACTIVE', 'FULL', NOW(), 100),
('KAKAO', 'female-user-006', 'female6@example.com', '강혜진', '혜진', '데이터사이언스학과', 20240206, 'ACTIVE', 'FULL', NOW(), 100),
('KAKAO', 'female-user-007', 'female7@example.com', '조하늘', '하늘', '정보통신학과', 20240207, 'ACTIVE', 'FULL', NOW(), 100),
('KAKAO', 'female-user-008', 'female8@example.com', '홍예지', '예지', '컴퓨터공학과', 20240208, 'ACTIVE', 'FULL', NOW(), 100),
('KAKAO', 'female-user-009', 'female9@example.com', '윤슬기', '슬기', '소프트웨어학과', 20240209, 'ACTIVE', 'FULL', NOW(), 100),
('KAKAO', 'female-user-010', 'female10@example.com', '배소연', '소연', '정보통신학과', 20240210, 'ACTIVE', 'FULL', NOW(), 100);

-- 2. user_profiles 데이터 삽입 (남성 10명)
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
) VALUES
-- 남성 10명 (user_id 1~10)
(1, 180, 'INTJ', 'NO', 'MALE', 'NOT_APPLICABLE', 'NONE', '서울 강남구', '안녕하세요! 개발을 좋아합니다', 'hong_gd', NOW()),
(2, 175, 'ISTP', 'NO', 'MALE', 'NOT_APPLICABLE', 'CHRISTIAN', '서울 송파구', '프로그래밍이 취미입니다', 'kim_cs', NOW()),
(3, 178, 'ENFP', 'NO', 'MALE', 'NOT_APPLICABLE', 'NONE', '경기 분당구', '새로운 사람 만나기를 좋아합니다', 'lee_yh', NOW()),
(4, 182, 'ESTJ', 'NO', 'MALE', 'NOT_APPLICABLE', 'CATHOLIC', '서울 강북구', '운동과 코딩 둘 다 좋아해요', 'park_jh', NOW()),
(5, 176, 'INFP', 'NO', 'MALE', 'NOT_APPLICABLE', 'NONE', '서울 마포구', '영화 보는 것을 좋아합니다', 'choi_mj', NOW()),
(6, 179, 'INTP', 'NO', 'MALE', 'NOT_APPLICABLE', 'NONE', '경기 용인시', 'AI에 관심이 많습니다', 'jung_sh', NOW()),
(7, 181, 'ENFJ', 'NO', 'MALE', 'NOT_APPLICABLE', 'CHRISTIAN', '서울 서초구', '따뜻한 마음으로 만나요', 'oh_jh', NOW()),
(8, 177, 'ESFP', 'NO', 'MALE', 'NOT_APPLICABLE', 'NONE', '서울 중구', '재미있는 일들을 함께 하고 싶어요', 'son_dw', NOW()),
(9, 174, 'ISFJ', 'NO', 'MALE', 'NOT_APPLICABLE', 'NONE', '경기 안양시', '차분하고 성실한 사람입니다', 'ryu_jh', NOW()),
(10, 183, 'ISTJ', 'NO', 'MALE', 'NOT_APPLICABLE', 'CATHOLIC', '서울 종로구', '책과 음악을 사랑합니다', 'shin_sm', NOW()),

-- 여성 10명 (user_id 11~20)
(11, 165, 'INTJ', 'NO', 'FEMALE', 'NOT_APPLICABLE', 'NONE', '서울 강남구', '똑똑하고 자유로운 마음', 'lee_jy', NOW()),
(12, 162, 'ISFP', 'NO', 'FEMALE', 'NOT_APPLICABLE', 'CHRISTIAN', '서울 강동구', '예술을 사랑하는 사람입니다', 'park_sj', NOW()),
(13, 168, 'ENFP', 'NO', 'FEMALE', 'NOT_APPLICABLE', 'NONE', '경기 수원시', '밝고 긍정적인 에너지입니다', 'kim_mj', NOW()),
(14, 160, 'ESFJ', 'NO', 'FEMALE', 'NOT_APPLICABLE', 'CATHOLIC', '서울 서대문구', '배려심 깊고 친절한 성격', 'jung_yn', NOW()),
(15, 167, 'INFP', 'NO', 'FEMALE', 'NOT_APPLICABLE', 'NONE', '서울 영등포구', '감수성 풍부한 영혼입니다', 'lee_sy', NOW()),
(16, 164, 'INTP', 'NO', 'FEMALE', 'NOT_APPLICABLE', 'NONE', '경기 성남시', '분석적이고 논리적입니다', 'kang_hj', NOW()),
(17, 169, 'ENFJ', 'NO', 'FEMALE', 'NOT_APPLICABLE', 'NONE', '서울 은평구', '리더십 있고 따뜻해요', 'jo_hn', NOW()),
(18, 161, 'ESFP', 'NO', 'FEMALE', 'NOT_APPLICABLE', 'CHRISTIAN', '서울 중랑구', '활발하고 재미있는 성격입니다', 'hong_yj', NOW()),
(19, 166, 'ISFJ', 'NO', 'FEMALE', 'NOT_APPLICABLE', 'NONE', '경기 부천시', '신뢰할 수 있는 사람입니다', 'yun_sg', NOW()),
(20, 163, 'ISTJ', 'NO', 'FEMALE', 'NOT_APPLICABLE', 'NONE', '서울 동작구', '차근차근 계획하는 성격이에요', 'bae_sy', NOW());

-- 3. 확인 쿼리
SELECT
  (SELECT COUNT(*) FROM users) as 총_사용자_수,
  (SELECT COUNT(*) FROM user_profiles WHERE gender = 'MALE') as 남성_프로필_수,
  (SELECT COUNT(*) FROM user_profiles WHERE gender = 'FEMALE') as 여성_프로필_수;

