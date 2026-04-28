package univ.airconnect.compatibility.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.springframework.stereotype.Component;

import univ.airconnect.compatibility.domain.CompatibilityFactor;
import univ.airconnect.compatibility.domain.CompatibilityGrade;
import univ.airconnect.compatibility.domain.CompatibilityProfile;
import univ.airconnect.compatibility.domain.CompatibilityResult;
import univ.airconnect.compatibility.domain.CompatibilityScoreDetail;
import univ.airconnect.compatibility.domain.MbtiCompatibilityTable;
import univ.airconnect.compatibility.domain.MbtiCompatibilityTier;

@Component
public class CompatibilityScoreCalculator {

    private static final int RAW_SCORE_MIN = 25;
    private static final int RAW_SCORE_MAX = 100;
    private static final int DISPLAY_SCORE_MIN = 50;
    private static final int DISPLAY_SCORE_MAX = 100;
    private static final int AGE_MAX = 15;
    private static final int DEPARTMENT_MAX = 12;
    private static final int STUDENT_NUMBER_MAX = 10;
    private static final int HEIGHT_MAX = 10;
    private static final int MBTI_MAX = 25;
    private static final int SMOKING_MAX = 10;
    private static final int RELIGION_MAX = 8;
    private static final int RESIDENCE_MAX = 10;

    public CompatibilityResult calculate(CompatibilityProfile me, CompatibilityProfile target) {
        MbtiCompatibilityTier mbtiTier = MbtiCompatibilityTable.tier(me.mbti(), target.mbti());

        List<CompatibilityScoreDetail> details = List.of(
                scoreAge(me.age(), target.age()),
                scoreDepartment(me.deptName(), target.deptName()),
                scoreStudentNumber(me.studentNum(), target.studentNum()),
                scoreHeight(me.height(), target.height()),
                scoreMbti(me.mbti(), target.mbti(), mbtiTier),
                scoreSmoking(me.smoking(), target.smoking()),
                scoreReligion(me.religion(), target.religion()),
                scoreResidence(me.residence(), target.residence())
        );

        int rawScore = details.stream()
                .mapToInt(CompatibilityScoreDetail::getScore)
                .sum();
        int totalScore = toDisplayScore(rawScore);

        return CompatibilityResult.builder()
                .myUserId(me.userId())
                .targetUserId(target.userId())
                .score(totalScore)
                .grade(toGrade(totalScore))
                .mbtiTier(mbtiTier)
                .reasons(topReasons(details))
                .cautions(topCautions(details))
                .details(details)
                .build();
    }

    private CompatibilityScoreDetail scoreAge(Integer myAge, Integer targetAge) {
        int diff = Math.abs(myAge - targetAge);
        int score;
        String reason;
        String caution;

        if (diff <= 1) {
            score = 15;
            reason = "나이 차이가 거의 없어 생활 리듬을 맞추기 좋아요.";
            caution = "나이 관점에서는 큰 주의점이 없어요.";
        } else if (diff <= 3) {
            score = 12;
            reason = "나이 차이가 부담스럽지 않은 편이에요.";
            caution = "서로의 학년이나 생활 속도가 다를 수 있어요.";
        } else if (diff <= 5) {
            score = 8;
            reason = "나이 차이가 있지만 대화로 충분히 맞춰갈 수 있어요.";
            caution = "관심사와 생활 패턴 차이를 초반에 확인해 보세요.";
        } else {
            score = 4;
            reason = "나이 차이가 뚜렷해 서로에게 새로운 관점을 줄 수 있어요.";
            caution = "나이 차이가 큰 편이라 기대하는 연애 속도가 다를 수 있어요.";
        }

        return detail(CompatibilityFactor.AGE, score, AGE_MAX, reason, caution);
    }

    private CompatibilityScoreDetail scoreDepartment(String myDeptName, String targetDeptName) {
        String my = normalizeText(myDeptName);
        String target = normalizeText(targetDeptName);

        int score;
        String reason;
        String caution;

        if (my.equals(target)) {
            score = 12;
            reason = "학과가 같아 수업, 과제, 학교 생활 이야기를 꺼내기 쉬워요.";
            caution = "같은 학과라 관계가 주변에 빨리 알려질 수 있어요.";
        } else if (my.contains(target) || target.contains(my)) {
            score = 9;
            reason = "학과명이 가까워 학교 생활의 공통 화제가 많을 수 있어요.";
            caution = "전공 세부 관심사는 다를 수 있으니 가볍게 물어보세요.";
        } else if (departmentGroup(my).equals(departmentGroup(target))) {
            score = 7;
            reason = "전공 계열이 비슷해 공부 방식이나 진로 이야기가 잘 이어질 수 있어요.";
            caution = "학과가 완전히 같지는 않아 캠퍼스 동선이 다를 수 있어요.";
        } else {
            score = 3;
            reason = "전공이 달라 서로의 세계를 새롭게 알아갈 수 있어요.";
            caution = "전공과 학교 생활의 접점은 적을 수 있어요.";
        }

        return detail(CompatibilityFactor.DEPARTMENT, score, DEPARTMENT_MAX, reason, caution);
    }

    private CompatibilityScoreDetail scoreStudentNumber(Integer myStudentNum, Integer targetStudentNum) {
        int myYear = admissionYear(myStudentNum);
        int targetYear = admissionYear(targetStudentNum);
        int diff = Math.abs(myYear - targetYear);

        int score;
        String reason;
        String caution;

        if (diff == 0) {
            score = 10;
            reason = "입학 시기가 같아 학교 경험과 주변 이슈를 공유하기 좋아요.";
            caution = "동기 문화가 겹칠 수 있어 공개 범위를 함께 정해두면 좋아요.";
        } else if (diff <= 1) {
            score = 8;
            reason = "학번 차이가 작아 캠퍼스 생활의 온도가 비슷할 가능성이 커요.";
            caution = "학년이 다르면 시험, 실습, 취업 준비 시기가 조금 다를 수 있어요.";
        } else if (diff <= 3) {
            score = 5;
            reason = "학번 차이가 있어 선후배처럼 도와줄 지점이 생길 수 있어요.";
            caution = "학번 차이에서 오는 학교 생활 경험 차이를 존중해 주세요.";
        } else {
            score = 2;
            reason = "학번 차이가 큰 만큼 서로 다른 캠퍼스 경험을 들려줄 수 있어요.";
            caution = "졸업, 취업, 군휴학 등 가까운 계획이 다를 가능성이 있어요.";
        }

        return detail(CompatibilityFactor.STUDENT_NUMBER, score, STUDENT_NUMBER_MAX, reason, caution);
    }

    private CompatibilityScoreDetail scoreHeight(Integer myHeight, Integer targetHeight) {
        int diff = Math.abs(myHeight - targetHeight);
        int score;
        String reason;
        String caution;

        if (diff <= 8) {
            score = 10;
            reason = "키 차이가 크지 않아 함께 사진을 찍거나 걷기 편한 편이에요.";
            caution = "키 관점에서는 큰 주의점이 없어요.";
        } else if (diff <= 15) {
            score = 8;
            reason = "키 차이가 자연스럽게 느껴질 수 있는 범위예요.";
            caution = "서로가 편하게 느끼는 거리감은 다를 수 있어요.";
        } else if (diff <= 25) {
            score = 5;
            reason = "키 차이가 뚜렷해 서로에게 다른 인상을 줄 수 있어요.";
            caution = "키 차이가 있는 편이라 상대가 선호하는 스타일을 단정하지 마세요.";
        } else {
            score = 2;
            reason = "키 차이가 매우 뚜렷해 첫인상이 강하게 남을 수 있어요.";
            caution = "키 차이를 농담으로 소비하지 않고 편안하게 대해 주세요.";
        }

        return detail(CompatibilityFactor.HEIGHT, score, HEIGHT_MAX, reason, caution);
    }

    private CompatibilityScoreDetail scoreMbti(String myMbti, String targetMbti, MbtiCompatibilityTier tier) {
        int score;
        String reason;
        String caution;

        if (tier == MbtiCompatibilityTier.IDEAL) {
            score = 25;
            reason = myMbti + "와 " + targetMbti + "는 성향 흐름이 잘 맞아 편하게 대화를 이어가기 좋은 조합이에요.";
            caution = "MBTI가 좋아도 실제 대화의 결은 직접 확인해야 해요.";
        } else if (tier == MbtiCompatibilityTier.POTENTIAL) {
            score = 17;
            reason = "MBTI 성향이 완전히 같지는 않지만, 대화를 나누면서 충분히 맞춰갈 수 있는 조합이에요.";
            caution = "성향 차이가 매력으로 느껴지는 지점을 천천히 찾아보세요.";
        } else {
            score = 7;
            reason = "MBTI 표에서는 신중하게 맞춰가야 하는 조합이에요.";
            caution = "대화 방식과 표현 차이를 초반에 천천히 살펴보세요.";
        }

        return detail(CompatibilityFactor.MBTI, score, MBTI_MAX, reason, caution);
    }

    private CompatibilityScoreDetail scoreSmoking(String mySmoking, String targetSmoking) {
        boolean matches = normalizeText(mySmoking).equals(normalizeText(targetSmoking));

        if (matches) {
            return detail(
                    CompatibilityFactor.SMOKING,
                    10,
                    SMOKING_MAX,
                    "흡연 여부가 같아 생활 습관에서 부딪힐 가능성이 낮아요.",
                    "흡연 여부는 같아도 냄새, 음주 자리, 주변 환경의 기준은 다를 수 있어요."
            );
        }

        return detail(
                CompatibilityFactor.SMOKING,
                2,
                SMOKING_MAX,
                "흡연 여부가 달라 서로의 생활 방식을 미리 이해할 기회가 있어요.",
                "흡연 여부가 달라 만나는 장소와 냄새에 대한 기준을 확인하는 게 좋아요."
        );
    }

    private CompatibilityScoreDetail scoreReligion(String myReligion, String targetReligion) {
        String my = normalizeText(myReligion);
        String target = normalizeText(targetReligion);

        int score;
        String reason;
        String caution;

        if (my.equals(target)) {
            score = 8;
            reason = "종교 정보가 같아 가치관 대화를 시작하기 편해요.";
            caution = "종교가 같아도 신앙의 깊이나 참여 빈도는 다를 수 있어요.";
        } else if (isNoReligion(my) || isNoReligion(target)) {
            score = 4;
            reason = "종교가 다르지만 한쪽이 무교라 조율 여지가 있어요.";
            caution = "종교 활동을 연애 안에서 어디까지 공유할지 미리 이야기해 보세요.";
        } else {
            score = 2;
            reason = "종교가 달라 가치관을 더 깊게 알아갈 대화가 생길 수 있어요.";
            caution = "종교가 서로 달라 예배, 모임, 기념일에 대한 기대가 다를 수 있어요.";
        }

        return detail(CompatibilityFactor.RELIGION, score, RELIGION_MAX, reason, caution);
    }

    private CompatibilityScoreDetail scoreResidence(String myResidence, String targetResidence) {
        ResidenceRegion my = ResidenceRegion.from(myResidence);
        ResidenceRegion target = ResidenceRegion.from(targetResidence);

        int score;
        String reason;
        String caution;

        if (Objects.equals(my.province(), target.province()) && Objects.equals(my.zone(), target.zone())) {
            score = 10;
            reason = "본가 권역이 가까워 방학이나 주말 동선을 맞추기 쉬울 수 있어요.";
            caution = "집이 가까우면 우연한 만남이나 사생활 경계가 더 중요할 수 있어요.";
        } else if (Objects.equals(my.province(), target.province())) {
            score = 8;
            reason = "본가가 같은 시도권에 있어 지역 이야기가 잘 통할 수 있어요.";
            caution = "같은 시도권이어도 실제 이동 시간은 길 수 있어요.";
        } else if (my.isCapitalArea() && target.isCapitalArea()) {
            score = 6;
            reason = "둘 다 수도권 권역이라 장기 일정 조율이 비교적 현실적일 수 있어요.";
            caution = "수도권 안에서도 통학, 귀가 동선은 크게 다를 수 있어요.";
        } else {
            score = 3;
            reason = "본가가 떨어져 있어 서로 다른 지역 이야기를 나눌 수 있어요.";
            caution = "방학과 명절처럼 본가에 머무는 기간에는 거리 부담이 생길 수 있어요.";
        }

        return detail(CompatibilityFactor.RESIDENCE, score, RESIDENCE_MAX, reason, caution);
    }

    private CompatibilityScoreDetail detail(
            CompatibilityFactor factor,
            int score,
            int maxScore,
            String reason,
            String caution
    ) {
        return CompatibilityScoreDetail.builder()
                .factor(factor)
                .score(score)
                .maxScore(maxScore)
                .reason(reason)
                .caution(caution)
                .build();
    }

    private List<String> topReasons(List<CompatibilityScoreDetail> details) {
        return details.stream()
                .sorted(Comparator.comparingInt(CompatibilityScoreDetail::getScore).reversed())
                .limit(3)
                .map(CompatibilityScoreDetail::getReason)
                .toList();
    }

    private List<String> topCautions(List<CompatibilityScoreDetail> details) {
        return details.stream()
                .sorted(Comparator.comparingDouble(CompatibilityScoreDetail::scoreRatio)
                        .thenComparingInt(CompatibilityScoreDetail::getScore))
                .limit(2)
                .map(CompatibilityScoreDetail::getCaution)
                .toList();
    }

    private CompatibilityGrade toGrade(int score) {
        if (score >= 90) {
            return CompatibilityGrade.AMAZING;
        }
        if (score >= 80) {
            return CompatibilityGrade.GOOD;
        }
        if (score >= 67) {
            return CompatibilityGrade.NORMAL;
        }
        return CompatibilityGrade.LOW;
    }

    private int toDisplayScore(int rawScore) {
        int boundedRawScore = Math.max(RAW_SCORE_MIN, Math.min(rawScore, RAW_SCORE_MAX));
        double normalized = (double) (boundedRawScore - RAW_SCORE_MIN) / (RAW_SCORE_MAX - RAW_SCORE_MIN);
        return (int) Math.round(DISPLAY_SCORE_MIN + normalized * (DISPLAY_SCORE_MAX - DISPLAY_SCORE_MIN));
    }

    private String departmentGroup(String department) {
        List<String> engineeringKeywords = List.of("컴퓨터", "소프트웨어", "정보", "통신", "데이터", "인공지능", "ai", "전산", "공학");
        List<String> businessKeywords = List.of("경영", "경제", "회계", "무역", "금융");
        List<String> humanitiesKeywords = List.of("국문", "영문", "언어", "문학", "사학", "철학");
        List<String> socialKeywords = List.of("사회", "심리", "행정", "정치", "언론", "미디어");
        List<String> naturalKeywords = List.of("수학", "물리", "화학", "생명", "통계");
        List<String> artsKeywords = List.of("디자인", "음악", "미술", "예술", "체육", "무용");
        List<String> healthKeywords = List.of("의학", "간호", "약학", "치위생", "보건");

        if (containsAny(department, engineeringKeywords)) {
            return "ENGINEERING";
        }
        if (containsAny(department, businessKeywords)) {
            return "BUSINESS";
        }
        if (containsAny(department, humanitiesKeywords)) {
            return "HUMANITIES";
        }
        if (containsAny(department, socialKeywords)) {
            return "SOCIAL";
        }
        if (containsAny(department, naturalKeywords)) {
            return "NATURAL";
        }
        if (containsAny(department, artsKeywords)) {
            return "ARTS";
        }
        if (containsAny(department, healthKeywords)) {
            return "HEALTH";
        }
        return department;
    }

    private boolean containsAny(String value, List<String> keywords) {
        return keywords.stream().anyMatch(value::contains);
    }

    private int admissionYear(Integer studentNum) {
        int value = studentNum;
        if (value >= 19000000 && value <= 20999999) {
            return value / 10000;
        }
        if (value >= 1900 && value <= 2099) {
            return value;
        }
        if (value >= 0 && value <= 99) {
            return value >= 70 ? 1900 + value : 2000 + value;
        }
        String text = String.valueOf(value);
        if (text.length() >= 4) {
            return Integer.parseInt(text.substring(0, 4));
        }
        return value;
    }

    private String normalizeText(String value) {
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    private boolean isNoReligion(String religion) {
        return religion.equals("none") || religion.equals("무교") || religion.equals("없음") || religion.equals("no");
    }

    private record ResidenceRegion(String province, String zone) {

        private static ResidenceRegion from(String residence) {
            String normalized = residence.trim();
            String province = normalized.split("\\s+")[0];
            return new ResidenceRegion(province, zoneOf(province, normalized));
        }

        private static String zoneOf(String province, String residence) {
            if (!province.equals("서울")) {
                return province;
            }

            List<String> east = new ArrayList<>(List.of("강남", "서초", "송파", "강동"));
            List<String> west = new ArrayList<>(List.of("강서", "양천", "구로", "금천", "영등포", "동작", "관악"));
            List<String> north = new ArrayList<>(List.of("도봉", "노원", "강북", "성북", "중랑", "동대문", "광진", "성동"));
            List<String> centerWest = new ArrayList<>(List.of("종로", "중구", "용산", "마포", "서대문", "은평"));

            if (east.stream().anyMatch(residence::contains)) {
                return "SEOUL_EAST";
            }
            if (west.stream().anyMatch(residence::contains)) {
                return "SEOUL_WEST";
            }
            if (north.stream().anyMatch(residence::contains)) {
                return "SEOUL_NORTH";
            }
            if (centerWest.stream().anyMatch(residence::contains)) {
                return "SEOUL_CENTER_WEST";
            }
            return "SEOUL";
        }

        private boolean isCapitalArea() {
            return province.equals("서울") || province.equals("경기") || province.equals("인천");
        }
    }
}
