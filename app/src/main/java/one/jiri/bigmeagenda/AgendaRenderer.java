package one.jiri.bigmeagenda;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.text.LineBreaker;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.ZoneId;

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
                layout(block.text, block.style).draw(canvas);
                canvas.restore();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) throw new IllegalStateException("PNG");
            return out.toByteArray();
        } finally { bitmap.recycle(); }
    }
    private StaticLayout layout(String text, AgendaPlan.Style style) {
        TextPaint paint = new TextPaint();
        paint.setColor(Color.BLACK);
        paint.setAntiAlias(false);
        paint.setTextSize(style == AgendaPlan.Style.EVENT ? 38 : style == AgendaPlan.Style.DAY ? 34 : 28);
        paint.setTypeface(style == AgendaPlan.Style.DAY ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        return StaticLayout.Builder.obtain(text, 0, text.length(), paint, AgendaPlan.CONTENT_WIDTH)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false)
                .setBreakStrategy(LineBreaker.BREAK_STRATEGY_SIMPLE).setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
                .setMaxLines(style == AgendaPlan.Style.EVENT ? 2 : 1).setEllipsize(TextUtils.TruncateAt.END).build();
    }
}
