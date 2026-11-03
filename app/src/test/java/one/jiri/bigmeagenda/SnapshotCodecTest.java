package one.jiri.bigmeagenda;

import static org.junit.Assert.*;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class SnapshotCodecTest {
    @Test public void roundTripsDatesFloatingAndUtcWithoutDeviceZone() throws Exception {
        AgendaSnapshot snapshot = new AgendaSnapshot(Instant.EPOCH, Instant.EPOCH.plusSeconds(1234), List.of(
                new AgendaEvent("ž", "Příliš žluťoučký", EventTime.date(LocalDate.of(2026,11,3)), EventTime.date(LocalDate.of(2026,11,4))),
                new AgendaEvent("f", "Plovoucí", EventTime.floating(LocalDateTime.of(2026,11,3,9,0)), EventTime.floating(LocalDateTime.of(2026,11,3,10,0))),
                new AgendaEvent("u", "UTC", EventTime.at(Instant.EPOCH), EventTime.at(Instant.EPOCH.plusSeconds(60)))));
        AgendaSnapshot restored = SnapshotCodec.decode(SnapshotCodec.encode(snapshot));
        assertEquals(snapshot.coverageEnd, restored.coverageEnd);
        for (int i=0;i<3;i++) {
            assertEquals(snapshot.events.get(i).title, restored.events.get(i).title);
            assertEquals(snapshot.events.get(i).start.kind, restored.events.get(i).start.kind);
            assertEquals(snapshot.events.get(i).start.value, restored.events.get(i).start.value);
            assertEquals(snapshot.events.get(i).end.value, restored.events.get(i).end.value);
        }
    }
    @Test public void rejectsTruncatedAndUnknownCache() throws Exception {
        byte[] encoded = SnapshotCodec.encode(new AgendaSnapshot(Instant.EPOCH, Instant.EPOCH, List.of()));
        assertThrows(IOException.class, () -> SnapshotCodec.decode(Arrays.copyOf(encoded, 8)));
        encoded[3] = 99;
        assertThrows(IOException.class, () -> SnapshotCodec.decode(encoded));
    }
}
