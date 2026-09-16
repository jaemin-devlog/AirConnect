-- AirConnect 입학 연도 정규화
-- 앱 API 필드명 studentNum은 하위 호환을 위해 유지하지만 DB 값은 0~99의 두 자리 연도만 저장한다.
-- 알려진 과거 형식(4자리 연도, 8자리/9자리 전체 학번)은 두 자리 연도로 변환하고,
-- 해석할 수 없는 값은 전체 학번 노출을 막기 위해 NULL로 정리한다.

START TRANSACTION;

UPDATE users
SET student_num = CASE
    WHEN student_num BETWEEN 0 AND 99 THEN student_num
    WHEN student_num BETWEEN 1900 AND 2099 THEN MOD(student_num, 100)
    WHEN student_num BETWEEN 19000000 AND 20999999 THEN MOD(FLOOR(student_num / 10000), 100)
    WHEN student_num BETWEEN 190000000 AND 209999999 THEN MOD(FLOOR(student_num / 100000), 100)
    ELSE NULL
END
WHERE student_num IS NOT NULL
  AND (student_num < 0 OR student_num > 99);

COMMIT;

SELECT
    COUNT(*) AS non_canonical_student_num_count
FROM users
WHERE student_num IS NOT NULL
  AND (student_num < 0 OR student_num > 99);
