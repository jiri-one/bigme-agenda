package one.jiri.bigmeagenda;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.util.AtomicFile;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

public final class MediaImage {
    private static final String PATH = "Pictures/BigmeAgenda/", NAME = "agenda.png";
    private final Context context;
    private final ContentResolver resolver;
    private final SharedPreferences prefs;
    private final AtomicFile lastGood;
    public MediaImage(Context context) {
        this.context = context;
        resolver = context.getContentResolver();
        prefs = context.getSharedPreferences("output", Context.MODE_PRIVATE);
        lastGood = new AtomicFile(new File(context.getNoBackupFilesDir(), "agenda-last.png"));
    }
    public static String outputPath(Context context) {
        return context.getSharedPreferences("output", Context.MODE_PRIVATE).getString("path", PATH + NAME);
    }
    public boolean publish(byte[] png) throws IOException {
        Uri uri = find();
        if (uri != null && prefs.getBoolean("dirty", false) && lastGood.getBaseFile().exists()) {
            write(uri, lastGood.readFully());
            finish(uri);
            save(prefs.edit().putBoolean("dirty", false));
        }
        byte[] digest = hash(png);
        // Another app may have changed the file since our last write.
        if (uri != null && !prefs.getBoolean("dirty", false) && Arrays.equals(digest, currentHash(uri))) return false;
        if (uri == null) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, NAME);
            values.put(MediaStore.Images.Media.RELATIVE_PATH, PATH);
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
            values.put(MediaStore.Images.Media.IS_PENDING, 1);
            uri = resolver.insert(collection(), values);
            if (uri == null) throw new IOException("media insert");
            String name = ownedName(uri);
            if (name == null) throw new IOException("media ownership");
            remember(uri, name);
        }
        save(prefs.edit().putBoolean("dirty", true));
        ContentValues pending = new ContentValues();
        pending.put(MediaStore.Images.Media.IS_PENDING, 1);
        if (resolver.update(uri, pending, null, null) != 1) throw new IOException("media pending");
        try {
            write(uri, png);
            if (!Arrays.equals(digest, currentHash(uri))) throw new IOException("media verify");
            finish(uri);
            FileOutputStream backup = lastGood.startWrite();
            try { backup.write(png); lastGood.finishWrite(backup); }
            catch (IOException e) { lastGood.failWrite(backup); throw e; }
            save(prefs.edit().putBoolean("dirty", false));
            return true;
        } catch (IOException | RuntimeException e) {
            if (lastGood.getBaseFile().exists()) {
                try { write(uri, lastGood.readFully()); finish(uri); save(prefs.edit().putBoolean("dirty", false)); }
                catch (IOException | RuntimeException ignored) { /* The next worker retries recovery. */ }
            }
            throw e;
        }
    }
    private Uri find() throws IOException {
        String saved = prefs.getString("uri", null);
        if (saved != null) {
            Uri uri = Uri.parse(saved);
            String name = ownedName(uri);
            if (name != null) { remember(uri, name); return uri; }
        }
        try (Cursor cursor = resolver.query(collection(), new String[]{MediaStore.Images.Media._ID},
                MediaStore.Images.Media.RELATIVE_PATH + "=? AND " + MediaStore.Images.Media.MIME_TYPE + "=? AND "
                        + MediaStore.Images.Media.OWNER_PACKAGE_NAME + "=?", new String[]{PATH, "image/png", context.getPackageName()},
                MediaStore.Images.Media._ID + " ASC")) {
            while (cursor != null && cursor.moveToNext()) {
                Uri uri = ContentUris.withAppendedId(collection(), cursor.getLong(0));
                String name = ownedName(uri);
                if (name != null) { remember(uri, name); return uri; }
            }
        }
        return null;
    }
    private String ownedName(Uri uri) {
        try (Cursor c = resolver.query(uri, new String[]{MediaStore.Images.Media.OWNER_PACKAGE_NAME,
                MediaStore.Images.Media.RELATIVE_PATH, MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.MIME_TYPE}, null, null, null)) {
            if (c == null || !c.moveToFirst() || !context.getPackageName().equals(c.getString(0))
                    || !PATH.equals(c.getString(1)) || !"image/png".equals(c.getString(3))) return null;
            String name = c.getString(2);
            return name != null && name.matches("agenda(?: \\(\\d+\\))?\\.png") ? name : null;
        }
    }
    private void remember(Uri uri, String name) throws IOException {
        String path = PATH + name;
        if (!uri.toString().equals(prefs.getString("uri", null)) || !path.equals(prefs.getString("path", null))) {
            save(prefs.edit().putString("uri", uri.toString()).putString("path", path));
        }
    }
    private static Uri collection() { return MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY); }
    private byte[] currentHash(Uri uri) throws IOException {
        try (InputStream in = resolver.openInputStream(uri)) {
            if (in == null) return new byte[0];
            return hash(CalDavClient.readBounded(in));
        }
    }
    private void write(Uri uri, byte[] bytes) throws IOException {
        ParcelFileDescriptor descriptor = resolver.openFileDescriptor(uri, "rw");
        if (descriptor == null) throw new IOException("media open");
        try (ParcelFileDescriptor.AutoCloseOutputStream out = new ParcelFileDescriptor.AutoCloseOutputStream(descriptor)) {
            FileChannel channel = out.getChannel();
            channel.position(0);
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) channel.write(buffer);
            channel.truncate(bytes.length);
            channel.force(true);
        }
    }
    private void finish(Uri uri) throws IOException {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.IS_PENDING, 0);
        values.put(MediaStore.Images.Media.DATE_MODIFIED, System.currentTimeMillis() / 1000);
        if (resolver.update(uri, values, null, null) != 1) throw new IOException("media finish");
    }
    private static byte[] hash(byte[] bytes) {
        try { return MessageDigest.getInstance("SHA-256").digest(bytes); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private static void save(SharedPreferences.Editor edit) throws IOException {
        if (!edit.commit()) throw new IOException("media state");
    }
}
