package univ.airconnect.admin.insights;

import java.time.*;
import java.time.temporal.ChronoUnit;
import univ.airconnect.global.error.BusinessException;
import univ.airconnect.global.error.ErrorCode;

/** Inclusive Korean calendar dates; SQL always uses half-open timestamp bounds. */
public record InsightWindow(LocalDate from, LocalDate to, Instant start, Instant end, Instant asOf) {
    public static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

    public static InsightWindow of(LocalDate from, LocalDate to, Clock clock) {
        Instant now = clock.instant();
        LocalDate today = now.atZone(KOREA).toLocalDate();
        if (to == null) to = today;
        if (from == null) from = to.minusDays(29);
        if (from.isAfter(to) || to.isAfter(today) || ChronoUnit.DAYS.between(from, to) >= 90)
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "오늘까지 최대 90일 범위를 선택하세요.");
        return new InsightWindow(from, to, from.atStartOfDay(KOREA).toInstant(),
                to.plusDays(1).atStartOfDay(KOREA).toInstant(), now);
    }

    public long days() { return ChronoUnit.DAYS.between(from, to) + 1; }
    public Instant observedEnd() { return end.isBefore(asOf) ? end : asOf; }
    public InsightWindow previous() {
        return new InsightWindow(from.minusDays(days()), from.minusDays(1),
                start.minus(days(), ChronoUnit.DAYS), start, asOf);
    }
}
