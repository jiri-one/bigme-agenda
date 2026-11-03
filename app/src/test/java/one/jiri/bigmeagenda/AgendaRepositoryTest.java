package one.jiri.bigmeagenda;

import static org.junit.Assert.*;
import android.content.Context;
import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE)
public class AgendaRepositoryTest {
    @Test public void replacedBoundaryWaitingForLockCannotDoWork() throws Exception { waitingBoundary(true); }
    @Test public void stoppedBoundaryWaitingForLockCannotDoWork() throws Exception { waitingBoundary(false); }

    private void waitingBoundary(boolean replace) throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("schedule", Context.MODE_PRIVATE).edit().putString("boundary_id", "first").commit();
        context.getSharedPreferences("status", Context.MODE_PRIVATE).edit().putString("message", "sentinel").commit();
        AgendaRepository repository = new AgendaRepository(context);
        Field lockField = AgendaRepository.class.getDeclaredField("LOCK");
        lockField.setAccessible(true);
        Object lock = lockField.get(null);
        AtomicBoolean stopped = new AtomicBoolean();
        AtomicReference<Throwable> error = new AtomicReference<>();
        CountDownLatch ready = new CountDownLatch(1);
        Thread worker = new Thread(() -> {
            ready.countDown();
            try { assertFalse(repository.redraw("first", stopped::get)); }
            catch (Throwable e) { error.set(e); }
        });
        synchronized (lock) {
            worker.start();
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            if (replace) context.getSharedPreferences("schedule", Context.MODE_PRIVATE).edit().putString("boundary_id", "replacement").commit();
            else stopped.set(true);
        }
        worker.join(5000);
        assertFalse(worker.isAlive());
        if (error.get() != null) throw new AssertionError(error.get());
        assertEquals("sentinel", context.getSharedPreferences("status", Context.MODE_PRIVATE).getString("message", null));
        assertTrue(Scheduler.currentBoundary(context, replace ? "replacement" : "first"));
        assertFalse(context.getSharedPreferences("output", Context.MODE_PRIVATE).contains("uri"));
    }
}
