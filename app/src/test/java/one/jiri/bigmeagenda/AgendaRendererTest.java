package one.jiri.bigmeagenda;

import static org.junit.Assert.*;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class AgendaRendererTest {
    @Test public void pngHasReservedWhiteClockAreaAndOnlyBlackInkBelow() {
        byte[] png = new AgendaRenderer().render(null, Instant.parse("2026-11-03T09:00:00Z"), ZoneId.of("Europe/Prague"));
        Bitmap bitmap = BitmapFactory.decodeByteArray(png, 0, png.length);
        assertEquals(824, bitmap.getWidth()); assertEquals(1648, bitmap.getHeight());
        int[] pixels = new int[824 * 1648]; bitmap.getPixels(pixels, 0, 824, 0, 0, 824, 1648);
        int ink = 0;
        for (int i=0;i<pixels.length;i++) {
            if (i < 824 * 420) assertEquals(Color.WHITE, pixels[i]);
            assertTrue(pixels[i] == Color.WHITE || pixels[i] == Color.BLACK);
            if (pixels[i] == Color.BLACK) ink++;
        }
        assertTrue(ink > 1000);
        bitmap.recycle();
    }
    @Test public void identicalVisibleAgendaProducesIdenticalBytes() {
        Instant now = Instant.parse("2026-11-03T09:00:00Z");
        AgendaSnapshot snapshot = new AgendaSnapshot(now, now.plusSeconds(864000), List.of(
                new AgendaEvent("synthetic", "Velmi dlouhá česká událost ".repeat(10), EventTime.at(now.plusSeconds(3600)), EventTime.at(now.plusSeconds(7200)))));
        AgendaRenderer renderer = new AgendaRenderer();
        assertArrayEquals(renderer.render(snapshot, now, ZoneId.of("Europe/Prague")),
                renderer.render(snapshot, now.plusSeconds(60), ZoneId.of("Europe/Prague")));
    }
}
