package one.jiri.bigmeagenda;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public final class PrivateStore {
    private static final String ALIAS = "bigme-agenda-v1";
    private final File directory;
    public PrivateStore(Context context) { directory = context.getNoBackupFilesDir(); }

    public CalendarConfig config() throws IOException, GeneralSecurityException {
        byte[] value = read("config");
        if (value == null) return null;
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(value))) {
            return new CalendarConfig(in.readUTF(), in.readUTF(), in.readUTF());
        } finally { Arrays.fill(value, (byte) 0); }
    }
    public void saveConfig(CalendarConfig config) throws IOException, GeneralSecurityException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeUTF(config.url); out.writeUTF(config.user); out.writeUTF(config.password);
        }
        byte[] value = bytes.toByteArray();
        try { write("config", value); } finally { Arrays.fill(value, (byte) 0); }
    }
    public AgendaSnapshot snapshot() throws IOException, GeneralSecurityException {
        byte[] value = read("events");
        if (value == null) return null;
        try { return SnapshotCodec.decode(value); } finally { Arrays.fill(value, (byte) 0); }
    }
    public void saveSnapshot(AgendaSnapshot snapshot) throws IOException, GeneralSecurityException { write("events", SnapshotCodec.encode(snapshot)); }
    public void clearSnapshot() { file("events").delete(); }
    private AtomicFile file(String name) { return new AtomicFile(new File(directory, name + ".enc")); }
    private SecretKey key() throws GeneralSecurityException, IOException {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (store.containsAlias(ALIAS)) return (SecretKey) store.getKey(ALIAS, null);
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build());
        return generator.generateKey();
    }
    private byte[] read(String name) throws GeneralSecurityException, IOException {
        AtomicFile file = file(name);
        if (!file.getBaseFile().exists()) return null;
        if (file.getBaseFile().length() > 8 * 1024 * 1024) throw new IOException("private size");
        byte[] bytes = file.readFully();
        if (bytes.length < 29 || bytes[0] != 1) throw new IOException("private format");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, bytes, 1, 12));
        cipher.updateAAD(name.getBytes(StandardCharsets.UTF_8));
        return cipher.doFinal(bytes, 13, bytes.length - 13);
    }
    private void write(String name, byte[] value) throws GeneralSecurityException, IOException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key());
        cipher.updateAAD(name.getBytes(StandardCharsets.UTF_8));
        byte[] encrypted = cipher.doFinal(value);
        AtomicFile target = file(name);
        FileOutputStream out = target.startWrite();
        try {
            out.write(1); out.write(cipher.getIV()); out.write(encrypted);
            target.finishWrite(out);
        } catch (IOException e) { target.failWrite(out); throw e; }
    }
}
