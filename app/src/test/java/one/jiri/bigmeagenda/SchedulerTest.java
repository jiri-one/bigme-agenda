package one.jiri.bigmeagenda;

import static org.junit.Assert.*;
import android.annotation.SuppressLint;
import android.content.Context;
import androidx.room.Room;
import androidx.work.Configuration;
import androidx.work.WorkInfo;
import androidx.work.impl.Processor;
import androidx.work.impl.WorkDatabase;
import androidx.work.impl.WorkManagerImpl;
import androidx.work.impl.utils.taskexecutor.WorkManagerTaskExecutor;
import java.time.Instant;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

// The real database makes the gap before a worker's terminal commit deterministic.
@SuppressLint("RestrictedApi")
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE, shadows = SchedulerTest.SyntheticStore.class)
public class SchedulerTest {
    private Context context;
    private WorkDatabase database;
    private WorkManagerImpl manager;

    @Before public void setup() throws Exception {
        context = RuntimeEnvironment.getApplication();
        Configuration config = new Configuration.Builder().setExecutor(Runnable::run).setTaskExecutor(Runnable::run).build();
        WorkManagerTaskExecutor tasks = new WorkManagerTaskExecutor(Runnable::run);
        database = Room.inMemoryDatabaseBuilder(context, WorkDatabase.class).allowMainThreadQueries().build();
        List<androidx.work.impl.Scheduler> schedulers = List.of();
        Processor processor = new Processor(context, config, tasks, database, schedulers);
        manager = new WorkManagerImpl(context, config, tasks, database, schedulers, processor);
        WorkManagerImpl.setDelegate(manager);
        SyntheticStore.config = new CalendarConfig("https://synthetic.invalid/old/", "old", "synthetic-old");
        SyntheticStore.snapshot = new AgendaSnapshot(Instant.EPOCH, Instant.EPOCH, List.of());
    }
    @After public void cleanup() {
        WorkManagerImpl.setDelegate(null);
        if (database != null) database.close();
    }
    @Test public void savingConfigGuaranteesFetchWhileOldResultIsUncommitted() throws Exception {
        WorkInfo old = runningFetch();
        AgendaRepository repository = new AgendaRepository(context);
        repository.save(new CalendarConfig("https://synthetic.invalid/new/", "new", "synthetic-new"));
        assertEquals("new", repository.config().user);
        assertNull(SyntheticStore.snapshot);
        List<WorkInfo> requests = manager.getWorkInfosForUniqueWork("manual-sync").get();
        assertEquals(1, requests.size());
        assertNotEquals(old.getId(), requests.get(0).getId());
        assertEquals(WorkInfo.State.ENQUEUED, requests.get(0).getState());
    }
    @Test public void normalRefreshCoalescesRunningFetch() throws Exception {
        WorkInfo old = runningFetch();
        new AgendaRepository(context).requestRefresh();
        List<WorkInfo> requests = manager.getWorkInfosForUniqueWork("manual-sync").get();
        assertEquals(1, requests.size());
        assertEquals(old.getId(), requests.get(0).getId());
        assertEquals(WorkInfo.State.RUNNING, requests.get(0).getState());
    }
    private WorkInfo runningFetch() throws Exception {
        Scheduler.refresh(context);
        WorkInfo old = manager.getWorkInfosForUniqueWork("manual-sync").get().get(0);
        database.workSpecDao().setState(WorkInfo.State.RUNNING, old.getId().toString());
        return old;
    }
    @Implements(value = PrivateStore.class, isInAndroidSdk = false)
    public static class SyntheticStore {
        static CalendarConfig config;
        static AgendaSnapshot snapshot;
        @Implementation protected void __constructor__(Context context) {}
        @Implementation protected CalendarConfig config() { return config; }
        @Implementation protected void saveConfig(CalendarConfig value) { config = value; }
        @Implementation protected AgendaSnapshot snapshot() { return snapshot; }
        @Implementation protected void clearSnapshot() { snapshot = null; }
    }
}
