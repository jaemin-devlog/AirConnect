package univ.airconnect.user.domain;

/** Public profile value: never expose the identifying suffix of a student number. */
public final class AdmissionYear {
    private AdmissionYear() {}
    public static Integer from(Integer studentNum) {
        if (studentNum == null) return null;
        if (studentNum >= 19000000 && studentNum <= 20999999) return studentNum / 10000;
        if (studentNum >= 1900 && studentNum <= 2099) return studentNum;
        if (studentNum >= 0 && studentNum <= 99) return studentNum >= 70 ? 1900 + studentNum : 2000 + studentNum;
        return null;
    }
}
