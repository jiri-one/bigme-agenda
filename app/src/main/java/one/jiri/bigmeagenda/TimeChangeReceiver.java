package one.jiri.bigmeagenda;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class TimeChangeReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_TIME_CHANGED.equals(action) || Intent.ACTION_TIMEZONE_CHANGED.equals(action)) Scheduler.redraw(context);
    }
}
