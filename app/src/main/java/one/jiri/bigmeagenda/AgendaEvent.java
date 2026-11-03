package one.jiri.bigmeagenda;

public final class AgendaEvent {
    public final String uid;
    public final String title;
    public final EventTime start;
    public final EventTime end;

    public AgendaEvent(String uid, String title, EventTime start, EventTime end) {
        this.uid = uid;
        this.title = title;
        this.start = start;
        this.end = end;
    }
    public boolean allDay() { return start.kind == EventTime.Kind.DATE; }
}
