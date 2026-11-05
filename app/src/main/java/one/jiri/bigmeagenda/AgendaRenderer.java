package one.jiri.bigmeagenda;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.text.LineBreaker;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.Locale;

public final class AgendaRenderer {
    public byte[] render(AgendaSnapshot snapshot, Instant now, ZoneId zone) {
        AgendaPlan plan = AgendaPlan.create(snapshot, now, zone, (text, style) -> layout(text, style).getHeight());
        Bitmap bitmap = Bitmap.createBitmap(AgendaPlan.WIDTH, AgendaPlan.HEIGHT, Bitmap.Config.ARGB_8888);
        try {
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(Color.WHITE);
            for (AgendaPlan.Block block : plan.blocks) {
                canvas.save();
                canvas.translate(AgendaPlan.MARGIN, block.y);
                if (block.style == AgendaPlan.Style.WEEK) {
                    drawWeek(canvas, plan);
                } else {
                    StaticLayout title = layout(block.text, block.style);
                    title.draw(canvas);
                    if (!block.detail.isEmpty()) {
                        canvas.translate(0, title.getHeight() + AgendaPlan.EVENT_TIME_GAP);
                        layout(block.detail, AgendaPlan.Style.TIME).draw(canvas);
                    }
                }
                canvas.restore();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) throw new IllegalStateException("PNG");
            return out.toByteArray();
        } finally { bitmap.recycle(); }
    }
    private void drawWeek(Canvas canvas, AgendaPlan plan) {
        Locale cs = Locale.forLanguageTag("cs-CZ");
        Paint paint = new Paint();
        paint.setAntiAlias(false);
        paint.setTextAlign(Paint.Align.CENTER);
        float cellWidth = AgendaPlan.CONTENT_WIDTH / 7f;
        for (int i = 0; i < 7; i++) {
            LocalDate date = plan.weekStart.plusDays(i);
            float x = (i + .5f) * cellWidth;
            paint.setColor(Color.BLACK);
            paint.setTypeface(Typeface.DEFAULT);
            paint.setTextSize(30);
            centeredText(canvas, date.getDayOfWeek().getDisplayName(TextStyle.SHORT, cs).toUpperCase(cs), x, 18, paint);
            boolean today = date.equals(plan.today);
            if (today) canvas.drawCircle(x, 78, 36, paint);
            paint.setColor(today ? Color.WHITE : Color.BLACK);
            paint.setTypeface(today ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            paint.setTextSize(44);
            centeredText(canvas, Integer.toString(date.getDayOfMonth()), x, 78, paint);
        }
        paint.setColor(Color.BLACK);
        paint.setStrokeWidth(2);
        canvas.drawLine(0, AgendaPlan.WEEK_HEIGHT - 1, AgendaPlan.CONTENT_WIDTH, AgendaPlan.WEEK_HEIGHT - 1, paint);
    }
    private void centeredText(Canvas canvas, String text, float x, float y, Paint paint) {
        Paint.FontMetrics metrics = paint.getFontMetrics();
        canvas.drawText(text, x, y - (metrics.ascent + metrics.descent) / 2, paint);
    }
    private StaticLayout layout(String text, AgendaPlan.Style style) {
        TextPaint paint = new TextPaint();
        paint.setColor(Color.BLACK);
        paint.setAntiAlias(false);
        paint.setTextSize(switch (style) {
            case EVENT -> 60;
            case TIME, DAY -> 44;
            case HEADER -> 42;
            default -> 36;
        });
        paint.setTypeface(style == AgendaPlan.Style.EVENT || style == AgendaPlan.Style.DAY || style == AgendaPlan.Style.HEADER ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        return StaticLayout.Builder.obtain(text, 0, text.length(), paint, AgendaPlan.CONTENT_WIDTH)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false)
                .setBreakStrategy(LineBreaker.BREAK_STRATEGY_SIMPLE).setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
                .setMaxLines(style == AgendaPlan.Style.EVENT ? 2 : 1).setEllipsize(TextUtils.TruncateAt.END).build();
    }
}
