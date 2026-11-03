package one.jiri.bigmeagenda;

import biweekly.ICalendar;
import biweekly.component.VEvent;
import biweekly.io.TimezoneAssignment;
import biweekly.io.text.ICalReader;
import biweekly.property.DateOrDateTimeProperty;
import biweekly.util.DateTimeComponents;
import biweekly.util.Duration;
import biweekly.util.ICalDate;
import com.github.mangstadt.vinnie.io.Context;
import com.github.mangstadt.vinnie.io.SyntaxRules;
import com.github.mangstadt.vinnie.SyntaxStyle;
import com.github.mangstadt.vinnie.io.VObjectDataAdapter;
import com.github.mangstadt.vinnie.io.VObjectReader;
import java.io.IOException;
import java.io.StringReader;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

public final class IcsParser {
    public static final int MAX_EVENTS = 4000;

    public List<AgendaEvent> parseExpanded(List<String> calendars, ZoneId zone) throws AgendaException {
        Map<String, Map<String, Candidate>> occurrences = new LinkedHashMap<>();
        int count = 0;
        try {
            for (String source : calendars) {
                if (!source.trim().startsWith("BEGIN:VCALENDAR") || !source.trim().endsWith("END:VCALENDAR")) fail();
                rejectEmbeddedTimezones(source);
                try (ICalReader reader = new ICalReader(source)) {
                    reader.setDefaultTimezone(TimeZone.getTimeZone(zone));
                    ICalendar calendar = reader.readNext();
                    if (calendar == null || hasErrors(reader) || calendar.getEvents().isEmpty()) fail();
                    for (VEvent event : calendar.getEvents()) {
                        if (++count > MAX_EVENTS) throw new AgendaException(AgendaException.Reason.SIZE);
                        if (event.getRecurrenceRule() != null || !event.getRecurrenceDates().isEmpty()
                                || !event.getExceptionDates().isEmpty() || !event.getExceptionRules().isEmpty()
                                || (event.getRecurrenceId() != null && event.getRecurrenceId().getRange() != null)) {
                            throw new AgendaException(AgendaException.Reason.EXPANSION);
                        }
                        if (event.getUid() == null || event.getUid().getValue() == null || event.getUid().getValue().isEmpty()
                                || event.getUid().getValue().length() > 1024) fail();
                        String uid = event.getUid().getValue();
                        boolean cancelled = event.getStatus() != null && event.getStatus().isCancelled();
                        EventTime start = time(event.getDateStart(), calendar);
                        EventTime recurrence = time(event.getRecurrenceId(), calendar);
                        if (start == null && (!cancelled || recurrence == null)) fail();
                        EventTime identity = recurrence != null ? recurrence : start;
                        AgendaEvent value = null;
                        if (!cancelled) {
                            EventTime end = end(event, start, calendar, zone);
                            if ((start.kind == EventTime.Kind.DATE) != (end.kind == EventTime.Kind.DATE)
                                    || end.instant(zone).isBefore(start.instant(zone))
                                    || (start.kind == EventTime.Kind.DATE && end.value.equals(start.value))) fail();
                            String title = event.getSummary() == null ? "" : event.getSummary().getValue();
                            value = new AgendaEvent(uid, cleanTitle(title), start, end);
                        }
                        int sequence = event.getSequence() == null ? 0 : event.getSequence().getValue();
                        long stamp = event.getDateTimeStamp() == null ? 0 : event.getDateTimeStamp().getValue().getTime();
                        Candidate candidate = new Candidate(value, sequence, stamp, recurrence != null, cancelled);
                        Map<String, Candidate> byTime = occurrences.computeIfAbsent(uid, k -> new LinkedHashMap<>());
                        byTime.merge(identity.identity(zone), candidate, IcsParser::newer);
                    }
                    if (reader.readNext() != null || hasErrors(reader)) fail();
                }
            }
            List<AgendaEvent> result = new ArrayList<>();
            for (Map<String, Candidate> byTime : occurrences.values()) {
                for (Candidate c : byTime.values()) if (!c.cancelled) result.add(c.event);
            }
            result.sort(Comparator.comparing((AgendaEvent e) -> e.start.instant(zone)).thenComparing(e -> e.uid));
            return result;
        } catch (AgendaException e) { throw e; }
        catch (Exception e) { throw new AgendaException(AgendaException.Reason.ICS); }
    }
    private static void rejectEmbeddedTimezones(String source) throws IOException, AgendaException {
        // CalDAV expand forbids VTIMEZONE; biweekly otherwise expands its rules eagerly.
        SyntaxRules rules = SyntaxRules.iCalendar();
        rules.setDefaultSyntaxStyle(SyntaxStyle.NEW);
        boolean[] found = {false};
        try (VObjectReader reader = new VObjectReader(new StringReader(source), rules)) {
            reader.parse(new VObjectDataAdapter() {
                @Override public void onComponentBegin(String name, Context context) {
                    if ("VTIMEZONE".equals(name)) { found[0] = true; context.stop(); }
                }
            });
        }
        if (found[0]) throw new AgendaException(AgendaException.Reason.EXPANSION);
    }
    private static boolean hasErrors(ICalReader reader) {
        // Warning 37 means a bare TZID was resolved using the system's time zone rules.
        return reader.getWarnings().stream().anyMatch(w -> !Integer.valueOf(37).equals(w.getCode()));
    }
    private static Candidate newer(Candidate a, Candidate b) {
        if (a.sequence != b.sequence) return a.sequence > b.sequence ? a : b;
        if (a.stamp != b.stamp) return a.stamp > b.stamp ? a : b;
        if (a.cancelled != b.cancelled) return a.cancelled ? a : b;
        if (a.detached != b.detached) return a.detached ? a : b;
        return a;
    }
    private static EventTime time(DateOrDateTimeProperty property, ICalendar calendar) throws AgendaException {
        if (property == null) return null;
        ICalDate date = property.getValue();
        if (date == null || date.getRawComponents() == null) { fail(); return null; }
        DateTimeComponents raw = date.getRawComponents();
        LocalDate day = LocalDate.of(raw.getYear(), raw.getMonth(), raw.getDate());
        if (!date.hasTime()) return EventTime.date(day);
        LocalDateTime local = day.atTime(raw.getHour(), raw.getMinute(), raw.getSecond());
        if (calendar.getTimezoneInfo().isFloating(property)) return EventTime.floating(local);
        String tzid = property.getParameters().getTimezoneId();
        if (!raw.isUtc() && calendar.getTimezoneInfo().getTimezone(property) == null && tzid != null) fail();
        return EventTime.at(date.toInstant());
    }
    private static EventTime end(VEvent event, EventTime start, ICalendar calendar, ZoneId zone) throws AgendaException {
        if (event.getDateEnd() != null) {
            if (event.getDuration() != null) fail();
            return time(event.getDateEnd(), calendar);
        }
        if (event.getDuration() == null) {
            return start.kind == EventTime.Kind.DATE ? EventTime.date(LocalDate.parse(start.value).plusDays(1)) : start;
        }
        Duration duration = event.getDuration().getValue();
        if (duration == null || duration.isPrior()) { fail(); return null; }
        long days = n(duration.getWeeks()) * 7 + n(duration.getDays());
        long seconds = n(duration.getHours()) * 3600 + n(duration.getMinutes()) * 60 + n(duration.getSeconds());
        if (start.kind == EventTime.Kind.DATE) {
            if (duration.hasTime() || days == 0) fail();
            return EventTime.date(LocalDate.parse(start.value).plusDays(days));
        }
        if (start.kind == EventTime.Kind.FLOATING) {
            return EventTime.floating(LocalDateTime.parse(start.value).plusDays(days).plusSeconds(seconds));
        }
        TimezoneAssignment assigned = calendar.getTimezoneInfo().getTimezone(event.getDateStart());
        if (assigned == null) return EventTime.at(start.instant(zone).plusSeconds(days * 86400 + seconds));
        // RFC 5545: calendar days precede exact hours across DST changes.
        java.util.Calendar end = java.util.Calendar.getInstance(assigned.getTimeZone());
        end.setTimeInMillis(start.instant(zone).toEpochMilli());
        end.add(java.util.Calendar.DAY_OF_MONTH, Math.toIntExact(days));
        return EventTime.at(end.toInstant().plusSeconds(seconds));
    }
    private static long n(Integer value) { return value == null ? 0 : value; }
    static String cleanTitle(String title) {
        if (title == null || title.trim().isEmpty()) return "Bez názvu";
        String clean = title.replaceAll("[\\p{Cc}\\p{Cf}\\s]+", " ").trim();
        if (clean.isEmpty()) return "Bez názvu";
        int end = clean.offsetByCodePoints(0, Math.min(300, clean.codePointCount(0, clean.length())));
        return clean.substring(0, end);
    }
    private static void fail() throws AgendaException { throw new AgendaException(AgendaException.Reason.ICS); }
    private static final class Candidate {
        final AgendaEvent event;
        final int sequence;
        final long stamp;
        final boolean detached, cancelled;
        Candidate(AgendaEvent event, int sequence, long stamp, boolean detached, boolean cancelled) {
            this.event = event; this.sequence = sequence; this.stamp = stamp;
            this.detached = detached; this.cancelled = cancelled;
        }
    }
}
