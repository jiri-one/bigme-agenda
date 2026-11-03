package one.jiri.bigmeagenda;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class AgendaPlan {
    public static final int WIDTH = 824, HEIGHT = 1648, TOP = 420, MARGIN = 44;
    public static final int CONTENT_WIDTH = WIDTH - 2 * MARGIN;
    private static final Locale CS = Locale.forLanguageTag("cs-CZ");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEEE d. M.", CS);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d. M.", CS);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", CS);
    public enum Style { HEADER, DAY, EVENT, NOTE }
    public static final class Block {
        public final String text;
        public final Style style;
        public final int y, height;
        Block(String text, Style style, int y, int height) { this.text = text; this.style = style; this.y = y; this.height = height; }
    }
    public interface Sizer { int height(String text, Style style); }
    public final List<Block> blocks;
    public final int omitted;
    private AgendaPlan(List<Block> blocks, int omitted) { this.blocks = blocks; this.omitted = omitted; }

    public static AgendaPlan create(AgendaSnapshot snapshot, Instant now, ZoneId zone, Sizer sizer) {
        LocalDate today = now.atZone(zone).toLocalDate();
        List<Row> rows = new ArrayList<>();
        rows.add(new Row("AGENDA  " + DATE.format(today) + " – " + DATE.format(today.plusDays(6)), Style.HEADER));
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
                for (AgendaEvent event : selected) rows.add(new Row(label(event, day, now, zone) + "  " + event.title, Style.EVENT));
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
            int height = sizer.height(row.text, row.style);
            int following = row.style == Style.DAY && i + 1 < rows.size() ? sizer.height(rows.get(i + 1).text, rows.get(i + 1).style) + 10 : 0;
            if (full || y + height + following > limit) {
                full = true;
                if (row.style == Style.EVENT) omitted++;
                continue;
            }
            blocks.add(new Block(row.text, row.style, y, height));
            y += height + (row.style == Style.EVENT ? 16 : 20);
        }
        if (omitted > 0) footer = "Další položky: " + omitted + (incomplete ? " · Starší data" : "");
        if (!footer.isEmpty()) blocks.add(new Block(footer, Style.NOTE, HEIGHT - MARGIN - footerHeight, footerHeight));
        return new AgendaPlan(blocks, omitted);
    }
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
        final String text; final Style style;
        Row(String text, Style style) { this.text = text; this.style = style; }
    }
}
