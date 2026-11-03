package one.jiri.bigmeagenda;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity implements SharedPreferences.OnSharedPreferenceChangeListener {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private EditText url, user, password;
    private Button save, refresh;
    private TextView status;
    private AgendaRepository repository;
    private SharedPreferences state;
    private boolean storedPassword;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        repository = new AgendaRepository(this);
        state = getSharedPreferences("status", MODE_PRIVATE);
        ScrollView scroll = new ScrollView(this);
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(24);
        column.setPadding(pad, pad, pad, pad);
        scroll.addView(column);
        addText(column, R.string.app_name, 28);
        addText(column, R.string.intro, 18);
        url = field(column, R.string.url_label, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        user = field(column, R.string.user_label, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        password = field(column, R.string.password_label, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        password.setHint(R.string.password_hint);
        password.setImeOptions(EditorInfo.IME_ACTION_DONE);
        password.setOnEditorActionListener((view, action, event) -> {
            if (action != EditorInfo.IME_ACTION_DONE) return false;
            if (save.isEnabled()) save();
            return true;
        });
        url.setHint(R.string.url_hint);
        save = button(column, R.string.save, this::save);
        refresh = button(column, R.string.refresh, () -> repository.requestRefresh());
        status = addText(column, R.string.status_unconfigured, 18);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        button(column, R.string.bigme_settings, () -> {
            try {
                startActivity(new Intent().setComponent(new ComponentName("com.xrz.standby", "com.xrz.settings.screensaver.ScreenSaverStyleSetActivity")));
            } catch (RuntimeException e) { status.setText(R.string.status_settings); }
        });
        addText(column, R.string.setup, 18);
        addText(column, R.string.timing, 16);
        addText(column, R.string.privacy, 16);
        setContentView(scroll);
        busy(true);
        executor.execute(() -> {
            try {
                CalendarConfig config = repository.config();
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    if (config != null) { url.setText(config.url); user.setText(config.user); storedPassword = true; password.setHint(R.string.password_saved); }
                    busy(false);
                });
                if (config != null) Scheduler.ensure(this);
                repository.redraw();
            } catch (IOException | GeneralSecurityException | RuntimeException e) {
                runOnUiThread(() -> { busy(false); status.setText(R.string.status_storage); });
            }
        });
    }
    private void save() {
        String address = url.getText().toString(), name = user.getText().toString(), secret = password.getText().toString();
        busy(true);
        executor.execute(() -> {
            try {
                String resolved = secret;
                if (resolved.isEmpty() && storedPassword) {
                    CalendarConfig previous = repository.config();
                    if (previous != null && previous.url.equals(address.trim()) && previous.user.equals(name.trim())) resolved = previous.password;
                }
                repository.save(new CalendarConfig(address, name, resolved));
                runOnUiThread(() -> { storedPassword = true; password.setText(""); password.setHint(R.string.password_saved); busy(false); });
            } catch (AgendaException e) {
                runOnUiThread(() -> { busy(false); status.setText(R.string.status_config); });
            } catch (IOException | GeneralSecurityException | RuntimeException e) {
                runOnUiThread(() -> { busy(false); status.setText(R.string.status_storage); });
            }
        });
    }
    private void busy(boolean busy) {
        save.setEnabled(!busy); refresh.setEnabled(!busy && storedPassword);
        url.setEnabled(!busy); user.setEnabled(!busy); password.setEnabled(!busy);
    }
    private TextView addText(LinearLayout column, int text, int size) {
        TextView view = new TextView(this);
        view.setText(text); view.setTextSize(size); view.setTextColor(android.graphics.Color.BLACK);
        view.setPadding(0, dp(10), 0, dp(10));
        column.addView(view, new LinearLayout.LayoutParams(-1, -2));
        return view;
    }
    private EditText field(LinearLayout column, int label, int type) {
        TextView title = addText(column, label, 18);
        EditText view = new EditText(this);
        view.setId(View.generateViewId()); title.setLabelFor(view.getId());
        view.setInputType(type); view.setTextSize(18); view.setMinHeight(dp(52));
        view.setSingleLine(true); view.setSelectAllOnFocus(true);
        view.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        view.setSaveEnabled(false); view.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        column.addView(view, new LinearLayout.LayoutParams(-1, -2));
        return view;
    }
    private Button button(LinearLayout column, int text, Runnable action) {
        Button button = new Button(this);
        button.setText(text); button.setTextSize(18); button.setAllCaps(false); button.setMinHeight(dp(56));
        button.setOnClickListener(v -> action.run());
        column.addView(button, new LinearLayout.LayoutParams(-1, -2));
        return button;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    @Override protected void onResume() { super.onResume(); state.registerOnSharedPreferenceChangeListener(this); updateStatus(); }
    @Override protected void onPause() { state.unregisterOnSharedPreferenceChangeListener(this); super.onPause(); }
    @Override protected void onDestroy() { executor.shutdown(); super.onDestroy(); }
    @Override public void onSharedPreferenceChanged(SharedPreferences prefs, String key) { updateStatus(); }
    private void updateStatus() {
        int message;
        switch (state.getString("message", "unconfigured")) {
            case "waiting": message = R.string.status_waiting; break;
            case "fetching": message = R.string.status_fetching; break;
            case "updated": message = R.string.status_updated; break;
            case "unchanged": message = R.string.status_unchanged; break;
            case "AUTH": message = R.string.status_auth; break;
            case "REDIRECT": message = R.string.status_redirect; break;
            case "HTTP": message = R.string.status_http; break;
            case "SIZE": message = R.string.status_size; break;
            case "XML": message = R.string.status_xml; break;
            case "ICS": message = R.string.status_ics; break;
            case "EXPANSION": message = R.string.status_expansion; break;
            case "STORAGE": message = R.string.status_storage; break;
            case "NETWORK": message = R.string.status_network; break;
            case "CONFIG": message = R.string.status_config; break;
            default: message = R.string.status_unconfigured;
        }
        long last = state.getLong("last_success", 0);
        String text = getString(message) + "\n" + getString(R.string.output_file, MediaImage.outputPath(this));
        if (last != 0) text += "\n" + getString(R.string.last_success, DateTimeFormatter.ofPattern("d. M. HH:mm", Locale.forLanguageTag("cs-CZ"))
                .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(last)));
        status.setText(text);
    }
}
