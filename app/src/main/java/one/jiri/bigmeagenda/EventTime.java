package one.jiri.bigmeagenda;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

public final class EventTime {
    public enum Kind { DATE, FLOATING, INSTANT }
    public final Kind kind;
    public final String value;

    public EventTime(Kind kind, String value) {
        this.kind = kind;
        this.value = value;
        instant(ZoneId.of("UTC"));
    }
    public static EventTime date(LocalDate value) { return new EventTime(Kind.DATE, value.toString()); }
    public static EventTime floating(LocalDateTime value) { return new EventTime(Kind.FLOATING, value.toString()); }
    public static EventTime at(Instant value) { return new EventTime(Kind.INSTANT, value.toString()); }
    public Instant instant(ZoneId zone) {
        if (kind == Kind.DATE) return LocalDate.parse(value).atStartOfDay(zone).toInstant();
        if (kind == Kind.FLOATING) return LocalDateTime.parse(value).atZone(zone).toInstant();
        return Instant.parse(value);
    }
    public String identity(ZoneId zone) {
        return kind == Kind.DATE ? "D:" + value : "T:" + instant(zone);
    }
}
