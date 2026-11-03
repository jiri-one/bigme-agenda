package one.jiri.bigmeagenda;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AgendaSnapshot {
    public final Instant fetchedAt;
    public final Instant coverageEnd;
    public final List<AgendaEvent> events;

    public AgendaSnapshot(Instant fetchedAt, Instant coverageEnd, List<AgendaEvent> events) {
        this.fetchedAt = fetchedAt;
        this.coverageEnd = coverageEnd;
        this.events = Collections.unmodifiableList(new ArrayList<>(events));
    }
}
