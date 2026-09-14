package univ.airconnect.user.domain;

/** Public profile value: expose only the two-digit admission year. */
public final class AdmissionYear {
    private AdmissionYear() {}
    public static Integer from(Integer studentNum) {
        if (studentNum == null) return null;
        if (studentNum >= 19000000 && studentNum <= 20999999) return (studentNum / 10000) % 100;
        if (studentNum >= 1900 && studentNum <= 2099) return studentNum % 100;
        if (studentNum >= 0 && studentNum <= 99) return studentNum;
        return null;
    }
}
