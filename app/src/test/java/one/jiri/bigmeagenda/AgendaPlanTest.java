package one.jiri.bigmeagenda;

import static org.junit.Assert.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;

public class AgendaPlanTest {
    private final ZoneId zone = ZoneId.of("Europe/Prague");
    private final Instant now = Instant.parse("2026-11-03T09:00:00Z");
    private final AgendaPlan.Sizer sizer = (text, style) -> style == AgendaPlan.Style.EVENT ? 90 : 40;
    private AgendaEvent event(String name, String start, String end) { return new AgendaEvent(name, name, EventTime.at(Instant.parse(start)), EventTime.at(Instant.parse(end))); }
    private AgendaSnapshot snapshot(List<AgendaEvent> events) { return new AgendaSnapshot(now, Instant.parse("2026-11-11T00:00:00Z"), events); }
    private AgendaPlan plan(List<AgendaEvent> events, Instant at) { return AgendaPlan.create(snapshot(events), at, zone, sizer); }
    private String text(AgendaPlan plan) { return plan.blocks.stream().map(b -> b.text + "\n" + b.detail).collect(Collectors.joining("\n")); }
    @Test public void excludesEndedAndIncludesOngoingAndUpcoming() {
        String output = text(plan(List.of(event("Skončená", "2026-11-03T07:00:00Z", "2026-11-03T09:00:00Z"),
                event("Běží", "2026-11-03T08:00:00Z", "2026-11-03T10:00:00Z"),
                event("Později", "2026-11-03T11:00:00Z", "2026-11-03T12:00:00Z")), now));
        assertFalse(output.contains("Skončená")); assertTrue(output.contains("Běží\nProbíhá · 09:00–11:00"));
        assertTrue(output.contains("Později\n12:00–13:00")); assertTrue(output.contains("Dnes · úterý 3. 11."));
    }
    @Test public void allDayEndExclusiveAndTimedBoundaryAreCorrect() {
        AgendaEvent allDay = new AgendaEvent("a", "Dva dny", EventTime.date(LocalDate.of(2026,11,3)), EventTime.date(LocalDate.of(2026,11,5)));
        String output = text(plan(List.of(allDay), now));
        assertEquals(2, output.split("Celý den", -1).length - 1);
        assertFalse(output.contains("čtvrtek"));
        assertFalse(text(plan(List.of(event("Osmý den", "2026-11-09T23:00:00Z", "2026-11-10T00:00:00Z")), now)).contains("Osmý den"));
    }
    @Test public void visibleContentDoesNotChangeEveryMinuteOrOnFetchTimestamp() {
        List<AgendaEvent> events = List.of(event("Porada", "2026-11-03T11:00:00Z", "2026-11-03T12:00:00Z"));
        assertEquals(text(plan(events, now)), text(plan(events, now.plusSeconds(60))));
        AgendaSnapshot refreshed = new AgendaSnapshot(now.plusSeconds(60), snapshot(events).coverageEnd, events);
        assertEquals(text(plan(events, now)), text(AgendaPlan.create(refreshed, now, zone, sizer)));
    }
    @Test public void reservesClockAreaAndReportsOverflowWithoutOrphanDate() {
        List<AgendaEvent> events = new ArrayList<>();
        for (int i=0;i<30;i++) events.add(event("Událost " + i, "2026-11-03T11:00:00Z", "2026-11-03T12:00:00Z"));
        AgendaPlan plan = plan(events, now);
        assertTrue(plan.omitted > 0);
        long shown = plan.blocks.stream().filter(b -> b.style == AgendaPlan.Style.EVENT).count();
        assertEquals(30, shown + plan.omitted);
        assertTrue(text(plan).contains("Další položky: " + plan.omitted));
        for (AgendaPlan.Block block : plan.blocks) { assertTrue(block.y >= 400); assertTrue(block.y + block.height <= AgendaPlan.HEIGHT); }
        assertNotEquals(AgendaPlan.Style.DAY, plan.blocks.get(plan.blocks.size()-1).style);
        for (int i = 0; i < plan.blocks.size(); i++) {
            if (plan.blocks.get(i).style == AgendaPlan.Style.DAY) {
                assertEquals(AgendaPlan.Style.EVENT, plan.blocks.get(i + 1).style);
            }
        }
    }
    @Test public void weekStartsOnMondayAndTracksLocalTodayAcrossYearBoundary() {
        Instant at = Instant.parse("2026-12-31T23:30:00Z");
        AgendaPlan plan = AgendaPlan.create(null, at, zone, sizer);
        assertEquals(LocalDate.of(2027, 1, 1), plan.today);
        assertEquals(LocalDate.of(2026, 12, 28), plan.weekStart);
        assertEquals(LocalDate.of(2027, 1, 3), plan.weekStart.plusDays(6));
        assertEquals("LEDEN 2027", plan.blocks.get(0).text);
        assertEquals(AgendaPlan.Style.WEEK, plan.blocks.get(1).style);
        AgendaPlan sunday = AgendaPlan.create(null, Instant.parse("2027-01-03T12:00:00Z"), zone, sizer);
        assertEquals(plan.weekStart, sunday.weekStart);
        AgendaPlan monday = AgendaPlan.create(null, Instant.parse("2027-01-04T12:00:00Z"), zone, sizer);
        assertEquals(LocalDate.of(2027, 1, 4), monday.weekStart);
    }
    @Test public void eventTitleAndTimeFitTogetherOrAreOmittedTogetherWithTheirDate() {
        AgendaSnapshot snapshot = snapshot(List.of(event("Dlouhý název", "2026-11-04T11:00:00Z", "2026-11-04T12:00:00Z")));
        AgendaPlan plan = AgendaPlan.create(snapshot, now, zone, (text, style) -> style == AgendaPlan.Style.EVENT ? 850 : 40);
        assertEquals(1, plan.omitted);
        assertFalse(text(plan).contains("Dlouhý název"));
        assertFalse(text(plan).contains("12:00–13:00"));
        assertFalse(text(plan).contains("Zítra"));
    }
    @Test public void nextChangeUsesStartsEndsAndLocalMidnightAcrossDst() {
        AgendaSnapshot snapshot = snapshot(List.of(event("Běží", "2026-11-03T08:00:00Z", "2026-11-03T10:00:00Z")));
        assertEquals(Instant.parse("2026-11-03T10:00:00Z"), AgendaPlan.nextChange(snapshot, now, zone));
        assertEquals(Instant.parse("2026-11-03T23:00:00Z"), AgendaPlan.nextChange(snapshot, now.plusSeconds(7200), zone));
        assertEquals(Instant.parse("2026-03-29T22:00:00Z"), AgendaPlan.nextChange(null, Instant.parse("2026-03-28T23:30:00Z"), zone));
    }
    @Test public void zeroDurationDisappearsImmediatelyAfterStart() {
        AgendaEvent point = event("Bod", "2026-11-03T09:00:00Z", "2026-11-03T09:00:00Z");
        assertTrue(text(plan(List.of(point), now)).contains("Bod"));
        assertFalse(text(plan(List.of(point), now.plusSeconds(1))).contains("Bod"));
        assertEquals(now.plusSeconds(1), AgendaPlan.nextChange(snapshot(List.of(point)), now, zone));
    }
    @Test public void expiredCoverageIsNeverReportedAsComplete() {
        AgendaSnapshot old = new AgendaSnapshot(now.minusSeconds(864000), now.plusSeconds(3600), List.of());
        assertTrue(text(AgendaPlan.create(old, now, zone, sizer)).contains("Starší data"));
    }
    @Test public void unconfiguredStateIsLegible() { assertTrue(text(AgendaPlan.create(null, now, zone, sizer)).contains("Kalendář zatím není načtený")); }
}
