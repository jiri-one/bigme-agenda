package one.jiri.bigmeagenda;

import static org.junit.Assert.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class IcsParserTest {
    private final ZoneId prague = ZoneId.of("Europe/Prague");
    static String calendar(String events) { return "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//BigmeAgenda Tests//CS\r\n" + events + "END:VCALENDAR\r\n"; }
    static String event(String uid, String body) { return "BEGIN:VEVENT\r\nUID:" + uid + "\r\nDTSTAMP:20261101T080000Z\r\n" + body + "END:VEVENT\r\n"; }
    private List<AgendaEvent> parse(String source) throws Exception { return new IcsParser().parseExpanded(List.of(calendar(source)), prague); }
    @Test public void unfoldsAndUnescapesCzechTitle() throws Exception {
        AgendaEvent event = parse(event("1", "DTSTART:20261103T080000Z\r\nSUMMARY:Porada\\, plán\\; podzim\\nDruhý "+
                "\r\n řádek\\\\konec\r\n")).get(0);
        assertEquals("Porada, plán; podzim Druhý řádek\\konec", event.title);
        assertEquals(Instant.parse("2026-11-03T08:00:00Z"), event.start.instant(prague));
    }
    @Test public void floatingTimesFollowDisplayZone() throws Exception {
        AgendaEvent event = parse(event("f", "DTSTART:20261103T090000\r\nDURATION:PT1H\r\n")).get(0);
        assertEquals(EventTime.Kind.FLOATING, event.start.kind);
        assertEquals(Instant.parse("2026-11-03T08:00:00Z"), event.start.instant(prague));
        assertEquals(Instant.parse("2026-11-03T14:00:00Z"), event.start.instant(ZoneId.of("America/New_York")));
    }
    @Test public void ianaZonePreservesDstAndIgnoresDeviceZone() throws Exception {
        AgendaEvent event = parse(event("z", "DTSTART;TZID=Europe/Prague:20260328T120000\r\nDURATION:P1D\r\n")).get(0);
        assertEquals(Instant.parse("2026-03-28T11:00:00Z"), event.start.instant(ZoneId.of("America/New_York")));
        assertEquals(Instant.parse("2026-03-29T10:00:00Z"), event.end.instant(prague));
    }
    @Test public void rejectsEmbeddedTimezoneInExpandedResponse() {
        String zone = "BEGIN:VTIMEZONE\r\nTZID:Custom/Test\r\nBEGIN:STANDARD\r\nDTSTART:19700101T000000\r\n"
                + "TZOFFSETFROM:+0230\r\nTZOFFSETTO:+0230\r\nEND:STANDARD\r\nEND:VTIMEZONE\r\n";
        AgendaException error = assertThrows(AgendaException.class, () -> parse(zone + event("z", "DTSTART:20261103T080000Z\r\n")));
        assertEquals(AgendaException.Reason.EXPANSION, error.reason);
    }
    @Test(timeout = 2000) public void rejectsTimezoneRecurrenceBeforeItsExpansionIncludingFoldedNames() {
        for (String begin : List.of("BEGIN:VTIMEZONE", "begin:vtimezone", "BE\r\n GIN:VTIMEZONE", "BEGIN:VTI\r\n MEZONE")) {
            String zone = begin + "\r\nTZID:Synthetic\r\nBEGIN:STANDARD\r\nDTSTART:20260101T000000\r\n"
                    + "TZOFFSETFROM:+0000\r\nTZOFFSETTO:+0000\r\nRRULE:FREQ=SECONDLY\r\nEND:STANDARD\r\nEND:VTIMEZONE\r\n";
            AgendaException error = assertThrows(AgendaException.class, () -> parse(zone + event("z", "DTSTART:20261103T080000Z\r\n")));
            assertEquals(AgendaException.Reason.EXPANSION, error.reason);
        }
    }
    @Test public void timezoneWordsInEventTextAreHarmless() throws Exception {
        AgendaEvent event = parse(event("text", "DTSTART:20261103T080000Z\r\nSUMMARY:BEGIN:VTIMEZONE\r\n")).get(0);
        assertEquals("BEGIN:VTIMEZONE", event.title);
    }
    @Test public void allDayEndIsExclusiveAndDefaultIsOneDay() throws Exception {
        List<AgendaEvent> events = parse(event("a", "DTSTART;VALUE=DATE:20261103\r\nDTEND;VALUE=DATE:20261105\r\n")
                + event("b", "DTSTART;VALUE=DATE:20261104\r\n"));
        assertEquals("2026-11-05", events.get(0).end.value);
        assertTrue(events.get(0).allDay());
        assertEquals(EventTime.date(LocalDate.of(2026,11,5)).value, events.get(1).end.value);
    }
    @Test public void expandedFirstOccurrenceMayOmitRecurrenceId() throws Exception {
        List<AgendaEvent> events = parse(event("series", "DTSTART:20261103T080000Z\r\nDURATION:PT1H\r\n")
                + event("series", "DTSTART:20261104T080000Z\r\nRECURRENCE-ID:20261104T080000Z\r\nDURATION:PT1H\r\n"));
        assertEquals(2, events.size());
    }
    @Test public void movedExceptionReplacesOriginalAndCancelledOccurrenceDisappears() throws Exception {
        List<AgendaEvent> events = parse(event("s", "DTSTART:20261103T080000Z\r\nSUMMARY:Original\r\n")
                + event("s", "RECURRENCE-ID:20261103T080000Z\r\nDTSTART:20261103T100000Z\r\nSEQUENCE:2\r\nSUMMARY:Přesun\r\n")
                + event("s", "DTSTART:20261104T080000Z\r\nRECURRENCE-ID:20261104T080000Z\r\n")
                + event("s", "RECURRENCE-ID:20261104T080000Z\r\nSTATUS:CANCELLED\r\nSEQUENCE:1\r\n"));
        assertEquals(1, events.size()); assertEquals("Přesun", events.get(0).title);
        assertEquals(Instant.parse("2026-11-03T10:00:00Z"), events.get(0).start.instant(prague));
    }
    @Test public void deduplicatesUidAndActualStartAcrossResources() throws Exception {
        String first = calendar(event("s", "DTSTART:20261103T080000Z\r\nSUMMARY:Staré\r\n"));
        String newer = calendar(event("s", "DTSTART:20261103T080000Z\r\nSEQUENCE:2\r\nSUMMARY:Nové\r\n"));
        List<AgendaEvent> events = new IcsParser().parseExpanded(Arrays.asList(first, newer, newer), prague);
        assertEquals(1, events.size()); assertEquals("Nové", events.get(0).title);
    }
    @Test public void movedOccurrenceCanShareStartWithAnotherOccurrence() throws Exception {
        List<AgendaEvent> events = parse(event("series", "RECURRENCE-ID:20261103T080000Z\r\nDTSTART:20261104T080000Z\r\nSUMMARY:Přesunuté úterý\r\n")
                + event("series", "RECURRENCE-ID:20261104T080000Z\r\nDTSTART:20261104T080000Z\r\nSUMMARY:Běžná středa\r\n"));
        assertEquals(2, events.size());
        assertEquals("Přesunuté úterý", events.get(0).title);
        assertEquals("Běžná středa", events.get(1).title);
        assertEquals(events.get(0).start.value, events.get(1).start.value);
    }
    @Test public void unrelatedEventsAtSameTimeRemainSeparate() throws Exception {
        assertEquals(2, parse(event("a", "DTSTART:20261103T080000Z\r\n") + event("b", "DTSTART:20261103T080000Z\r\n")).size());
    }
    @Test public void rejectsUnexpandedRulesDatesAndRangeInsteadOfLosingOccurrences() throws Exception {
        for (String property : List.of("RRULE:FREQ=DAILY", "RDATE:20261104T080000Z", "EXDATE:20261104T080000Z",
                "RECURRENCE-ID;RANGE=THISANDFUTURE:20261103T080000Z")) {
            AgendaException error = assertThrows(AgendaException.class, () -> parse(event("s", "DTSTART:20261103T080000Z\r\n" + property + "\r\n")));
            assertEquals(AgendaException.Reason.EXPANSION, error.reason);
        }
    }
    @Test public void rejectsUnknownTimezoneMalformedDateAndTruncation() {
        for (String body : List.of("DTSTART;TZID=Not/AZone:20261103T080000\r\n", "DTSTART:bad\r\n", "DTSTART:20260230T080000Z\r\n",
                "DTSTART;VALUE=DATE:20261103\r\nDTEND:20261103T090000Z\r\n")) {
            assertThrows(AgendaException.class, () -> parse(event("x", body)));
        }
        assertThrows(AgendaException.class, () -> new IcsParser().parseExpanded(List.of("BEGIN:VCALENDAR\nBEGIN:VEVENT\n"), prague));
    }
    @Test public void zeroDurationHasNoInventedHour() throws Exception {
        AgendaEvent event = parse(event("point", "DTSTART:20261103T080000Z\r\n")).get(0);
        assertEquals(event.start.value, event.end.value);
    }
}
