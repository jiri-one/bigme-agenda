package one.jiri.bigmeagenda;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class SnapshotCodec {
    private SnapshotCodec() {}
    public static byte[] encode(AgendaSnapshot snapshot) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(buffer)) {
            out.writeInt(1);
            out.writeLong(snapshot.fetchedAt.toEpochMilli());
            out.writeLong(snapshot.coverageEnd.toEpochMilli());
            out.writeInt(snapshot.events.size());
            for (AgendaEvent e : snapshot.events) {
                out.writeUTF(e.uid); out.writeUTF(e.title);
                writeTime(out, e.start); writeTime(out, e.end);
            }
        }
        return buffer.toByteArray();
    }
    public static AgendaSnapshot decode(byte[] bytes) throws IOException {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (in.readInt() != 1) throw new IOException("cache version");
            Instant fetched = Instant.ofEpochMilli(in.readLong()), until = Instant.ofEpochMilli(in.readLong());
            int count = in.readInt();
            if (count < 0 || count > IcsParser.MAX_EVENTS) throw new IOException("cache size");
            List<AgendaEvent> events = new ArrayList<>();
            for (int i = 0; i < count; i++) events.add(new AgendaEvent(in.readUTF(), in.readUTF(), readTime(in), readTime(in)));
            if (in.read() != -1) throw new IOException("cache trailing data");
            return new AgendaSnapshot(fetched, until, events);
        } catch (RuntimeException e) { throw new IOException("cache format"); }
    }
    private static void writeTime(DataOutputStream out, EventTime time) throws IOException {
        out.writeUTF(time.kind.name()); out.writeUTF(time.value);
    }
    private static EventTime readTime(DataInputStream in) throws IOException {
        return new EventTime(EventTime.Kind.valueOf(in.readUTF()), in.readUTF());
    }
}
