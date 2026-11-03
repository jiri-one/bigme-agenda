package one.jiri.bigmeagenda;

import java.io.IOException;

public final class AgendaException extends IOException {
    public enum Reason { CONFIG, AUTH, REDIRECT, HTTP, SIZE, XML, ICS, EXPANSION, STORAGE, NETWORK }
    public final Reason reason;
    public final boolean retryable;

    public AgendaException(Reason reason) { this(reason, false); }
    public AgendaException(Reason reason, boolean retryable) {
        super(reason.name());
        this.reason = reason;
        this.retryable = retryable;
    }
}
