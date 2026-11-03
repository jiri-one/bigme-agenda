package one.jiri.bigmeagenda;

import static org.junit.Assert.*;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowContentResolver;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE)
public class MediaImageTest {
    private Context context;
    private ImageProvider provider;
    private MediaImage image;
    @Before public void setup() throws Exception {
        context = RuntimeEnvironment.getApplication();
        provider = new ImageProvider(new File(context.getCacheDir(), "owned.png"), context.getPackageName());
        android.content.pm.ProviderInfo info = new android.content.pm.ProviderInfo();
        info.authority = "media";
        provider.attachInfo(context, info);
        ShadowContentResolver.registerProviderInternal("media", provider);
        image = new MediaImage(context);
    }
    @Test public void rewritesSameRowAndNeverTouchesUnchangedOutput() throws Exception {
        assertTrue(image.publish(new byte[]{1,2,3}));
        assertEquals(1, provider.inserts); assertEquals(0, provider.pending);
        int writes = provider.writes, updates = provider.updates;
        assertFalse(image.publish(new byte[]{1,2,3}));
        assertEquals(writes, provider.writes); assertEquals(updates, provider.updates);
        assertTrue(new MediaImage(context).publish(new byte[]{4,5}));
        assertEquals(1, provider.inserts); assertArrayEquals(new byte[]{4,5}, Files.readAllBytes(provider.file.toPath()));
        assertEquals("Pictures/BigmeAgenda/", provider.inserted.getAsString(MediaStore.Images.Media.RELATIVE_PATH));
        assertEquals("agenda.png", provider.inserted.getAsString(MediaStore.Images.Media.DISPLAY_NAME));
    }
    @Test public void failedWriteRestoresLastGoodBytesAndVisibility() throws Exception {
        image.publish(new byte[]{1,2,3});
        provider.failNextWrite = true;
        assertThrows(IOException.class, () -> image.publish(new byte[]{4,5}));
        assertArrayEquals(new byte[]{1,2,3}, Files.readAllBytes(provider.file.toPath()));
        assertEquals(0, provider.pending); assertEquals(1, provider.inserts);
        assertTrue(image.publish(new byte[]{4,5}));
    }
    @Test public void recoversProcessDeathDuringExistingFileRewrite() throws Exception {
        image.publish(new byte[]{1,2,3});
        Files.write(provider.file.toPath(), new byte[]{99});
        provider.pending = 1;
        context.getSharedPreferences("output", Context.MODE_PRIVATE).edit().putBoolean("dirty",true).commit();
        new MediaImage(context).publish(new byte[]{1,2,3});
        assertEquals(0, provider.pending); assertEquals(1, provider.inserts);
        assertArrayEquals(new byte[]{1,2,3}, Files.readAllBytes(provider.file.toPath()));
    }
    @Test public void processDeathBeforeFirstPublishCannotLeaveMatchingBytesPending() throws Exception {
        image.publish(new byte[]{1,2,3});
        assertTrue(new File(context.getNoBackupFilesDir(), "agenda-last.png").delete());
        provider.pending = 1;
        context.getSharedPreferences("output", Context.MODE_PRIVATE).edit().putBoolean("dirty",true).commit();
        assertTrue(new MediaImage(context).publish(new byte[]{1,2,3}));
        assertEquals(0, provider.pending); assertEquals(1, provider.inserts);
    }
    @Test public void retriesFirstFailedWriteWithoutCreatingAnotherRow() throws Exception {
        provider.failNextWrite = true;
        assertThrows(IOException.class, () -> image.publish(new byte[]{7}));
        assertEquals(1, provider.pending);
        assertTrue(image.publish(new byte[]{7}));
        assertEquals(1, provider.inserts); assertEquals(0, provider.pending);
    }
    @Test public void retainsProviderRenamedImageAfterNameCollision() throws Exception {
        provider.assignedName = "agenda (1).png";
        assertTrue(image.publish(new byte[]{1,2,3}));
        assertTrue(new MediaImage(context).publish(new byte[]{4,5}));
        assertEquals(1, provider.inserts);
        assertEquals("Pictures/BigmeAgenda/agenda (1).png", MediaImage.outputPath(context));
        assertArrayEquals(new byte[]{4,5}, Files.readAllBytes(provider.file.toPath()));
    }
    @Test public void recoversRenamedOwnedRowAfterLosingSavedUri() throws Exception {
        provider.assignedName = "agenda (1).png";
        image.publish(new byte[]{1,2,3});
        context.getSharedPreferences("output", Context.MODE_PRIVATE).edit().clear().commit();
        assertTrue(new MediaImage(context).publish(new byte[]{4,5}));
        assertEquals(1, provider.inserts);
        assertEquals("Pictures/BigmeAgenda/agenda (1).png", MediaImage.outputPath(context));
    }
    private static final class ImageProvider extends ContentProvider {
        final File file;
        final String owner;
        ContentValues inserted;
        int inserts, writes, updates, pending;
        boolean failNextWrite;
        String assignedName = "agenda.png";
        ImageProvider(File file, String owner) { this.file = file; this.owner = owner; }
        @Override public boolean onCreate() { return true; }
        @Override public String getType(Uri uri) { return "image/png"; }
        @Override public Uri insert(Uri uri, ContentValues values) {
            inserts++; inserted = new ContentValues(values); pending = 1;
            try { Files.write(file.toPath(), new byte[0]); } catch (IOException e) { throw new IllegalStateException(e); }
            return Uri.withAppendedPath(uri, "1");
        }
        @Override public Cursor query(Uri uri, String[] columns, String selection, String[] args, String sort) {
            MatrixCursor cursor = new MatrixCursor(columns);
            if (inserts == 0) return cursor;
            Object[] row = new Object[columns.length];
            for (int i=0;i<columns.length;i++) {
                switch(columns[i]) {
                    case MediaStore.Images.Media._ID: row[i] = 1; break;
                    case MediaStore.Images.Media.OWNER_PACKAGE_NAME: row[i] = owner; break;
                    case MediaStore.Images.Media.RELATIVE_PATH: row[i] = "Pictures/BigmeAgenda/"; break;
                    case MediaStore.Images.Media.DISPLAY_NAME: row[i] = assignedName; break;
                    case MediaStore.Images.Media.MIME_TYPE: row[i] = "image/png"; break;
                    default: throw new AssertionError(columns[i]);
                }
            }
            cursor.addRow(row); return cursor;
        }
        @Override public int update(Uri uri, ContentValues values, String selection, String[] args) {
            updates++; if (values.containsKey(MediaStore.Images.Media.IS_PENDING)) pending = values.getAsInteger(MediaStore.Images.Media.IS_PENDING);
            return 1;
        }
        @Override public int delete(Uri uri, String selection, String[] args) { throw new AssertionError("Must retain row"); }
        @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
            if (mode.contains("w")) {
                writes++;
                if (failNextWrite) { failNextWrite = false; throw new FileNotFoundException("Synthetic failure"); }
            }
            return ParcelFileDescriptor.open(file, mode.contains("w") ? ParcelFileDescriptor.MODE_READ_WRITE : ParcelFileDescriptor.MODE_READ_ONLY);
        }
    }
}
