package one.jiri.bigmeagenda;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.function.BooleanSupplier;

public final class AgendaRepository {
    private static final Object LOCK = new Object();
    private final Context context;
    private final PrivateStore store;
    private final SharedPreferences state;
    public AgendaRepository(Context context) {
        this.context = context.getApplicationContext();
        store = new PrivateStore(this.context);
        state = this.context.getSharedPreferences("status", Context.MODE_PRIVATE);
    }
    public CalendarConfig config() throws IOException, GeneralSecurityException { synchronized (LOCK) { return store.config(); } }
    public void save(CalendarConfig config) throws IOException, GeneralSecurityException {
        synchronized (LOCK) {
            CalendarConfig old;
            try { old = store.config(); } catch (IOException | GeneralSecurityException e) { old = null; }
            store.saveConfig(config);
            if (old == null || !old.url.equals(config.url) || !old.user.equals(config.user)) store.clearSnapshot();
            Scheduler.ensure(context);
            status("waiting");
            Scheduler.refreshAfterSave(context);
        }
    }
    public void requestRefresh() { status("waiting"); Scheduler.refresh(context); }
    public boolean sync() {
        synchronized (LOCK) {
            try {
                CalendarConfig config;
                try { config = store.config(); }
                catch (IOException e) { throw new AgendaException(AgendaException.Reason.STORAGE); }
                if (config == null) { status("unconfigured"); return false; }
                status("fetching");
                ZoneId zone = ZoneId.systemDefault();
                Instant now = Instant.now();
                LocalDate today = now.atZone(zone).toLocalDate();
                List<String> data = new CalDavClient().fetch(config, today.minusDays(2).atStartOfDay(zone).toInstant(),
                        today.plusDays(9).atStartOfDay(zone).toInstant());
                List<AgendaEvent> events = new IcsParser().parseExpanded(data, zone);
                AgendaSnapshot snapshot = new AgendaSnapshot(now, today.plusDays(8).atStartOfDay(zone).toInstant(), events);
                try { store.saveSnapshot(snapshot); }
                catch (IOException e) { throw new AgendaException(AgendaException.Reason.STORAGE, true); }
                boolean changed = render(snapshot, Instant.now(), zone, null, () -> false);
                state.edit().putLong("last_success", now.toEpochMilli()).apply();
                status(changed ? "updated" : "unchanged");
                return false;
            } catch (AgendaException e) {
                redrawAfterFailure();
                status(e.reason.name());
                return e.retryable;
            } catch (IOException e) {
                redrawAfterFailure();
                status("NETWORK");
                return true;
            } catch (GeneralSecurityException | RuntimeException e) {
                status("STORAGE");
                return false;
            }
        }
    }
    public boolean redraw() { return redraw(null, () -> false); }
    boolean redraw(String boundaryId, BooleanSupplier stopped) {
        synchronized (LOCK) {
            if (!active(boundaryId, stopped)) return false;
            try {
                AgendaSnapshot snapshot = store.snapshot();
                CalendarConfig config = store.config();
                if (snapshot == null && config != null) return false;
                render(snapshot, Instant.now(), ZoneId.systemDefault(), boundaryId, stopped);
                if (config != null) Scheduler.ensure(context);
                return false;
            } catch (IOException | GeneralSecurityException | RuntimeException e) {
                status("STORAGE");
                return true;
            }
        }
    }
    private void redrawAfterFailure() {
        try {
            AgendaSnapshot snapshot = store.snapshot();
            if (snapshot != null) render(snapshot, Instant.now(), ZoneId.systemDefault(), null, () -> false);
        } catch (IOException | GeneralSecurityException | RuntimeException ignored) { /* Keep the last readable output. */ }
    }
    private boolean active(String boundaryId, BooleanSupplier stopped) {
        return !stopped.getAsBoolean() && (boundaryId == null || Scheduler.currentBoundary(context, boundaryId));
    }
    private boolean render(AgendaSnapshot snapshot, Instant now, ZoneId zone, String boundaryId, BooleanSupplier stopped) throws AgendaException {
        try {
            boolean changed = new MediaImage(context).publish(new AgendaRenderer().render(snapshot, now, zone));
            if (snapshot != null && active(boundaryId, stopped)) Scheduler.boundary(context, snapshot, now, zone, boundaryId != null);
            return changed;
        } catch (IOException | RuntimeException e) { throw new AgendaException(AgendaException.Reason.STORAGE, true); }
    }
    private void status(String value) { state.edit().putString("message", value).apply(); }
}
