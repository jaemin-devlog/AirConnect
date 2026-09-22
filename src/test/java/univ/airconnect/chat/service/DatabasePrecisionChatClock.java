package univ.airconnect.chat.service;

import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.time.Clock;
import java.time.Duration;

/** Test-thread-only clock: time still advances, but matches H2 timestamp(6) precision. */
final class DatabasePrecisionChatClock {
    private DatabasePrecisionChatClock() { }

    static MockedStatic<Clock> open() {
        // Without truncation, an immediate DB round-trip can round a new event's
        // due time (or a member's join time) into the future relative to now.
        Clock databasePrecision = Clock.tick(Clock.systemUTC(), Duration.ofNanos(1_000));
        MockedStatic<Clock> clock = Mockito.mockStatic(Clock.class, Mockito.CALLS_REAL_METHODS);
        clock.when(Clock::systemUTC).thenReturn(databasePrecision);
        return clock;
    }
}
