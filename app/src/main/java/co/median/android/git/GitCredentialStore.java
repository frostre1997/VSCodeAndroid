package co.median.android.git;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Map;
import java.util.TreeMap;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Encrypted key/value store for Git secrets (HTTPS passwords/tokens, SSH key
 * passphrases, commit identity). Values are AES-256-GCM encrypted with a key
 * held in the Android Keystore, so they never leave the device in plaintext.
 */
public class GitCredentialStore {

    private static final String PREFS_NAME = "vscode_git_credentials";
    private static final String KEYSTORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "vscode_git_credentials_key";
    private static final int GCM_TAG_BITS = 128;

    public static final String KEY_IDENTITY_NAME = "identity.name";
    public static final String KEY_IDENTITY_EMAIL = "identity.email";

    private final SharedPreferences prefs;
    private final SecretKey secretKey;

    public GitCredentialStore(Context context) throws GeneralSecurityException, IOException {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.secretKey = loadOrCreateKey();
    }

    private SecretKey loadOrCreateKey() throws GeneralSecurityException, IOException {
        KeyStore keyStore = KeyStore.getInstance(KEYSTORE);
        keyStore.load(null);
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            KeyGenerator generator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);
            generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build());
            generator.generateKey();
        }
        KeyStore.SecretKeyEntry entry = (KeyStore.SecretKeyEntry) keyStore.getEntry(KEY_ALIAS, null);
        if (entry == null) {
            throw new GeneralSecurityException("AndroidKeyStore entry missing");
        }
        return entry.getSecretKey();
    }

    /** Store a value; an empty or null value removes the key. */
    public void put(String key, String value) {
        if (value == null || value.isEmpty()) {
            remove(key);
            return;
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] iv = cipher.getIV();
            byte[] cipherText = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));

            ByteBuffer buffer = ByteBuffer.allocate(4 + iv.length + cipherText.length);
            buffer.putInt(iv.length);
            buffer.put(iv);
            buffer.put(cipherText);
            prefs.edit().putString(key, Base64.encodeToString(buffer.array(), Base64.NO_WRAP)).apply();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to encrypt credential", e);
        }
    }

    public String get(String key) {
        String encoded = prefs.getString(key, null);
        if (encoded == null) return null;
        try {
            byte[] data = Base64.decode(encoded, Base64.NO_WRAP);
            ByteBuffer buffer = ByteBuffer.wrap(data);
            int ivLength = buffer.getInt();
            byte[] iv = new byte[ivLength];
            buffer.get(iv);
            byte[] cipherText = new byte[buffer.remaining()];
            buffer.get(cipherText);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (Exception e) {
            // The value is corrupt or the key changed; treat as absent.
            return null;
        }
    }

    public void remove(String key) {
        prefs.edit().remove(key).apply();
    }

    /** All decrypted entries (for the settings/key-management UI). */
    public Map<String, String> all() {
        Map<String, String> result = new TreeMap<>();
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            if (entry.getValue() instanceof String) {
                String value = get(entry.getKey());
                if (value != null) result.put(entry.getKey(), value);
            }
        }
        return result;
    }

    /** Standard key for a remote URL's credentials. */
    public static String remoteKey(String prefix, String url) {
        return prefix + "." + Integer.toHexString(url.hashCode());
    }
}
