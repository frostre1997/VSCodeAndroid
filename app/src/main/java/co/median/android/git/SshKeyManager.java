package co.median.android.git;

import android.util.Base64;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Generation, storage and listing of SSH key pairs for Git remotes. Keys are
 * written to the app-private SSH directory (see {@link GitStorage}); the
 * private key uses unencrypted PKCS#8 PEM so JGit's SSH transport can load it
 * without prompting. The public half is written in standard OpenSSH format.
 */
public class SshKeyManager {

    public static class SshKeyInfo {
        public String name;          // e.g. "id_rsa"
        public String privateKeyPath;
        public String publicKeyPath;
        public String fingerprint;   // sha256 fingerprint of the public key
        public String comment;
    }

    private final File sshDir;
    private final GitCredentialStore store;

    public SshKeyManager(File sshDir, GitCredentialStore store) {
        this.sshDir = sshDir;
        this.store = store;
    }

    public SshKeyInfo generateKey(String comment, String passphrase) throws IOException {
        if (comment == null) comment = "android";
        KeyPair pair;
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            pair = generator.generateKeyPair();
        } catch (Exception e) {
            throw new IOException("Failed to generate RSA key", e);
        }

        String name = "id_rsa";
        File privateKey = new File(sshDir, name);
        File publicKey = new File(sshDir, name + ".pub");

        try (FileOutputStream out = new FileOutputStream(privateKey)) {
            out.write(pemEncode("PRIVATE KEY", pair.getPrivate().getEncoded()).getBytes());
        }
        privateKey.setReadable(false, false);
        privateKey.setReadable(true, true);

        String pubLine = opensshPublicKey(pair.getPublic(), comment);
        writeTextFile(publicKey, pubLine + "\n");

        if (passphrase != null && !passphrase.isEmpty()) {
            store.put("ssh." + name + ".passphrase", passphrase);
        }

        return infoFor(name);
    }

    public List<SshKeyInfo> listKeys() {
        List<SshKeyInfo> result = new ArrayList<>();
        File[] files = sshDir.listFiles();
        if (files == null) return result;
        Arrays.sort(files, Comparator.comparing(File::getName));
        for (File f : files) {
            if (f.isFile() && f.getName().endsWith(".pub")) {
                String name = f.getName().substring(0, f.getName().length() - 4);
                if (new File(sshDir, name).isFile()) {
                    result.add(infoFor(name));
                }
            }
        }
        return result;
    }

    public SshKeyInfo getKey(String name) {
        return infoFor(name);
    }

    public boolean hasKey(String name) {
        return new File(sshDir, name).isFile();
    }

    public void deleteKey(String name) {
        new File(sshDir, name).delete();
        new File(sshDir, name + ".pub").delete();
        store.remove("ssh." + name + ".passphrase");
    }

    public String publicKeyContents(String name) throws IOException {
        File pub = new File(sshDir, name + ".pub");
        if (!pub.isFile()) return null;
        return readTextFile(pub).trim();
    }

    private SshKeyInfo infoFor(String name) {
        SshKeyInfo info = new SshKeyInfo();
        info.name = name;
        info.privateKeyPath = new File(sshDir, name).getAbsolutePath();
        info.publicKeyPath = new File(sshDir, name + ".pub").getAbsolutePath();
        info.fingerprint = fingerprint(new File(sshDir, name + ".pub"));
        return info;
    }

    private String fingerprint(File pubFile) {
        try {
            String line = readTextFile(pubFile).trim();
            if (line.isEmpty()) return null;
            String[] parts = line.split("\\s+");
            if (parts.length < 2) return null;
            byte[] keyData = Base64.decode(parts[1], Base64.DEFAULT);
            byte[] hash = java.security.MessageDigest.getInstance("SHA-256").digest(keyData);
            String b64 = Base64.encodeToString(hash, Base64.NO_WRAP | Base64.NO_PADDING).replace("+", "-").replace("/", "_");
            return "SHA256:" + b64;
        } catch (Exception e) {
            return null;
        }
    }

    private static String readTextFile(File file) throws IOException {
        java.io.InputStream in = new java.io.FileInputStream(file);
        try {
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        } finally {
            in.close();
        }
    }

    private static void writeTextFile(File file, String content) throws IOException {
        FileOutputStream out = new FileOutputStream(file);
        try {
            out.write(content.getBytes(StandardCharsets.UTF_8));
        } finally {
            out.close();
        }
    }

    private static String pemEncode(String label, byte[] der) {
        String b64 = Base64.encodeToString(der, Base64.NO_WRAP);
        StringBuilder sb = new StringBuilder("-----BEGIN ").append(label).append("-----\n");
        for (int i = 0; i < b64.length(); i += 64) {
            sb.append(b64, i, Math.min(i + 64, b64.length())).append('\n');
        }
        sb.append("-----END ").append(label).append("-----\n");
        return sb.toString();
    }

    private static String opensshPublicKey(PublicKey publicKey, String comment) {
        if (!(publicKey instanceof RSAPublicKey)) {
            throw new IllegalArgumentException("Expected RSA public key");
        }
        RSAPublicKey rsa = (RSAPublicKey) publicKey;
        byte[] blob = new byte[0];
        blob = concat(blob, sshString("ssh-rsa"));
        blob = concat(blob, sshMpint(rsa.getPublicExponent()));
        blob = concat(blob, sshMpint(rsa.getModulus()));
        return "ssh-rsa " + Base64.encodeToString(blob, Base64.NO_WRAP) + " " + comment;
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    private static byte[] sshString(String s) {
        byte[] bytes = s.getBytes(StandardCharsets.US_ASCII);
        ByteBufferSafe b = new ByteBufferSafe();
        b.putInt(bytes.length);
        b.put(bytes);
        return b.toByteArray();
    }

    private static byte[] sshMpint(BigInteger value) {
        byte[] bytes = value.toByteArray();
        ByteBufferSafe b = new ByteBufferSafe();
        b.putInt(bytes.length);
        b.put(bytes);
        return b.toByteArray();
    }

    private static class ByteBufferSafe {
        private final java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();

        void putInt(int v) {
            out.write((v >>> 24) & 0xFF);
            out.write((v >>> 16) & 0xFF);
            out.write((v >>> 8) & 0xFF);
            out.write(v & 0xFF);
        }

        void put(byte[] data) {
            out.write(data, 0, data.length);
        }

        byte[] toByteArray() {
            return out.toByteArray();
        }
    }
}
