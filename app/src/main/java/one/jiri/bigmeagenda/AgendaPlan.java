package one.jiri.bigmeagenda;

import java.time.Instant;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class AgendaPlan {
    public static final int WIDTH = 824, HEIGHT = 1648, TOP = 420, MARGIN = 44;
    public static final int CONTENT_WIDTH = WIDTH - 2 * MARGIN;
    public static final int WEEK_HEIGHT = 128, EVENT_TIME_GAP = 4;
    private static final Locale CS = Locale.forLanguageTag("cs-CZ");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEEE d. M.", CS);
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("LLLL yyyy", CS);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", CS);
    public enum Style { HEADER, WEEK, DAY, EVENT, TIME, NOTE }
    public static final class Block {
        public final String text, detail;
        public final Style style;
        public final int y, height;
        Block(Row row, int y, int height) {
            this.text = row.text; this.detail = row.detail; this.style = row.style; this.y = y; this.height = height;
        }
    }
    public interface Sizer { int height(String text, Style style); }
    public final List<Block> blocks;
    public final int omitted;
    public final LocalDate today, weekStart;
    private AgendaPlan(List<Block> blocks, int omitted, LocalDate today) {
        this.blocks = blocks; this.omitted = omitted; this.today = today;
        this.weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    public static AgendaPlan create(AgendaSnapshot snapshot, Instant now, ZoneId zone, Sizer sizer) {
        LocalDate today = now.atZone(zone).toLocalDate();
        List<Row> rows = new ArrayList<>();
        rows.add(new Row(MONTH.format(today).toUpperCase(CS), Style.HEADER));
        rows.add(new Row("", Style.WEEK));
        if (snapshot == null) {
            rows.add(new Row("Kalendář zatím není načtený.", Style.NOTE));
            rows.add(new Row("Otevřete Bigme Agenda a uložte přihlášení.", Style.EVENT));
        } else {
            for (int i = 0; i < 7; i++) {
                LocalDate day = today.plusDays(i);
                Instant dayStart = day.atStartOfDay(zone).toInstant();
                Instant dayEnd = day.plusDays(1).atStartOfDay(zone).toInstant();
                List<AgendaEvent> selected = new ArrayList<>();
                for (AgendaEvent event : snapshot.events) {
                    Instant start = event.start.instant(zone), end = event.end.instant(zone);
                    boolean point = start.equals(end);
                    boolean active = point ? !start.isBefore(now) : end.isAfter(now);
                    boolean intersects = start.isBefore(dayEnd) && (point ? !start.isBefore(dayStart) : end.isAfter(dayStart));
                    if (active && intersects) selected.add(event);
                }
                selected.sort(Comparator.comparing((AgendaEvent e) -> !e.allDay())
                        .thenComparing(e -> e.start.instant(zone)).thenComparing(e -> e.title).thenComparing(e -> e.uid));
                if (selected.isEmpty() && i != 0) continue;
                rows.add(new Row((i == 0 ? "Dnes · " : i == 1 ? "Zítra · " : "") + DAY.format(day), Style.DAY));
                if (selected.isEmpty()) rows.add(new Row("Žádné další události", Style.NOTE));
                for (AgendaEvent event : selected) rows.add(new Row(event.title, label(event, day, now, zone), Style.EVENT));
            }
        }
        boolean incomplete = snapshot != null && today.plusDays(7).atStartOfDay(zone).toInstant().isAfter(snapshot.coverageEnd);
        String footer = incomplete ? "Starší data · obnovte kalendář" : "";
        int footerHeight = sizer.height("Další položky: 9999 · Starší data", Style.NOTE) + 16;
        int limit = HEIGHT - MARGIN - footerHeight;
        List<Block> blocks = new ArrayList<>();
        int y = TOP, omitted = 0;
        boolean full = false;
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            int height = row.height(sizer);
            int following = row.style == Style.DAY && i + 1 < rows.size() ? rows.get(i + 1).height(sizer) + gap(row.style) : 0;
            if (full || y + height + following > limit) {
                full = true;
                if (row.style == Style.EVENT) omitted++;
                continue;
            }
            blocks.add(new Block(row, y, height));
            y += height + gap(row.style);
        }
        if (omitted > 0) footer = "Další položky: " + omitted + (incomplete ? " · Starší data" : "");
        if (!footer.isEmpty()) blocks.add(new Block(new Row(footer, Style.NOTE), HEIGHT - MARGIN - footerHeight, footerHeight));
        return new AgendaPlan(blocks, omitted, today);
    }
    private static int gap(Style style) { return style == Style.WEEK ? 22 : style == Style.HEADER || style == Style.DAY ? 14 : 18; }
    private static String label(AgendaEvent event, LocalDate day, Instant now, ZoneId zone) {
        if (event.allDay()) return "Celý den";
        Instant start = event.start.instant(zone), end = event.end.instant(zone);
        String prefix = day.equals(now.atZone(zone).toLocalDate()) && !start.isAfter(now) && end.isAfter(now) ? "Probíhá · " : "";
        String from = start.atZone(zone).toLocalDate().isBefore(day) ? "…" : TIME.format(start.atZone(zone));
        String until = end.atZone(zone).toLocalDate().isAfter(day) ? "…" : TIME.format(end.atZone(zone));
        return prefix + from + (start.equals(end) ? "" : "–" + until);
    }
    public static Instant nextChange(AgendaSnapshot snapshot, Instant now, ZoneId zone) {
        Instant next = now.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant();
        if (snapshot != null) for (AgendaEvent event : snapshot.events) {
            Instant start = event.start.instant(zone), end = event.end.instant(zone);
            if (start.isAfter(now) && start.isBefore(next)) next = start;
            if (end.isAfter(now) && end.isBefore(next)) next = end;
            if (start.equals(end) && start.equals(now)) next = now.plusSeconds(1);
        }
        return next;
    }
    private static final class Row {
        final String text, detail; final Style style;
        Row(String text, Style style) { this(text, "", style); }
        Row(String text, String detail, Style style) { this.text = text; this.detail = detail; this.style = style; }
        int height(Sizer sizer) {
            if (style == Style.WEEK) return WEEK_HEIGHT;
            return sizer.height(text, style) + (detail.isEmpty() ? 0 : EVENT_TIME_GAP + sizer.height(detail, Style.TIME));
        }
    }
}
