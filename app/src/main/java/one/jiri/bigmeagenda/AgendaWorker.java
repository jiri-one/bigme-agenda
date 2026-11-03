package one.jiri.bigmeagenda;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public final class AgendaWorker extends Worker {
    public AgendaWorker(@NonNull Context context, @NonNull WorkerParameters parameters) { super(context, parameters); }
    @NonNull @Override public Result doWork() {
        if (isStopped()) return Result.success();
        AgendaRepository repository = new AgendaRepository(getApplicationContext());
        String mode = getInputData().getString("mode");
        boolean retry = "sync".equals(mode) ? repository.sync()
                : repository.redraw("boundary".equals(mode) ? getId().toString() : null, this::isStopped);
        return retry && getRunAttemptCount() < 3 ? Result.retry() : Result.success();
    }
}
