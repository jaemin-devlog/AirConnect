package univ.airconnect.user.domain;

/**
 * 앱에 노출하고 신규 회원가입에 저장하는 표준값은 두 자리 입학 연도다.
 * 과거 DB의 4자리 연도와 8자리/9자리 전체 학번은 읽을 때만 호환 변환한다.
 */
public final class AdmissionYear {
    private AdmissionYear() {}

    public static Integer from(Integer studentNum) {
        if (studentNum == null) return null;
        if (studentNum >= 190000000 && studentNum <= 209999999) return (studentNum / 100000) % 100;
        if (studentNum >= 19000000 && studentNum <= 20999999) return (studentNum / 10000) % 100;
        if (studentNum >= 1900 && studentNum <= 2099) return studentNum % 100;
        if (studentNum >= 0 && studentNum <= 99) return studentNum;
        return null;
    }

    public static boolean isCanonical(Integer value) {
        return value != null && value >= 0 && value <= 99;
    }
}
