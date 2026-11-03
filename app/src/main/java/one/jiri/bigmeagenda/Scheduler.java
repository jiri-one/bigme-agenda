package one.jiri.bigmeagenda;

import android.content.Context;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.TimeUnit;

public final class Scheduler {
    private Scheduler() {}
    private static Constraints network() { return new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(); }
    public static void ensure(Context context) {
        WorkManager manager = WorkManager.getInstance(context);
        manager.enqueueUniquePeriodicWork("calendar-sync", ExistingPeriodicWorkPolicy.KEEP,
                new PeriodicWorkRequest.Builder(AgendaWorker.class, 30, TimeUnit.MINUTES)
                        .setInitialDelay(30, TimeUnit.MINUTES).setConstraints(network()).setInputData(data("sync")).build());
        manager.enqueueUniquePeriodicWork("cached-redraw", ExistingPeriodicWorkPolicy.KEEP,
                new PeriodicWorkRequest.Builder(AgendaWorker.class, 30, TimeUnit.MINUTES)
                        .setInitialDelay(30, TimeUnit.MINUTES).setInputData(data("redraw")).build());
    }
    public static void refresh(Context context) {
        enqueueRefresh(context, ExistingWorkPolicy.KEEP);
    }
    static void refreshAfterSave(Context context) {
        enqueueRefresh(context, ExistingWorkPolicy.REPLACE);
    }
    private static void enqueueRefresh(Context context, ExistingWorkPolicy policy) {
        WorkManager.getInstance(context).enqueueUniqueWork("manual-sync", policy,
                new OneTimeWorkRequest.Builder(AgendaWorker.class).setConstraints(network()).setInputData(data("sync")).build());
    }
    public static void redraw(Context context) {
        WorkManager.getInstance(context).enqueueUniqueWork("manual-redraw", ExistingWorkPolicy.REPLACE,
                new OneTimeWorkRequest.Builder(AgendaWorker.class).setInputData(data("redraw")).build());
    }
    static void boundary(Context context, AgendaSnapshot snapshot, Instant now, ZoneId zone, boolean fromBoundary) {
        Instant next = AgendaPlan.nextChange(snapshot, now, zone);
        android.content.SharedPreferences prefs = context.getSharedPreferences("schedule", Context.MODE_PRIVATE);
        if (!fromBoundary && prefs.getLong("boundary", 0) == next.toEpochMilli()) return;
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(AgendaWorker.class)
                .setInitialDelay(Math.max(1000, Duration.between(now, next).toMillis()), TimeUnit.MILLISECONDS)
                .setInputData(data("boundary")).build();
        // Appending lets a boundary worker finish without cancelling itself.
        WorkManager.getInstance(context).enqueueUniqueWork("next-boundary",
                fromBoundary ? ExistingWorkPolicy.APPEND_OR_REPLACE : ExistingWorkPolicy.REPLACE, request);
        prefs.edit().putLong("boundary", next.toEpochMilli()).putString("boundary_id", request.getId().toString()).apply();
    }
    static boolean currentBoundary(Context context, String id) {
        return id.equals(context.getSharedPreferences("schedule", Context.MODE_PRIVATE).getString("boundary_id", null));
    }
    private static Data data(String mode) { return new Data.Builder().putString("mode", mode).build(); }
}
