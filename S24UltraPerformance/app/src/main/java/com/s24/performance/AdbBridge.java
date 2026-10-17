package com.s24.performance;

import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import io.github.muntashirakon.adb.AbsAdbConnectionManager;
import io.github.muntashirakon.adb.AdbStream;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.Certificate;
import java.util.Date;
import java.util.concurrent.*;

public final class AdbBridge extends AbsAdbConnectionManager {
    public interface Callback { void finished(String output, boolean success); }
    private static AdbBridge INSTANCE;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final PrivateKey key;
    private final Certificate certificate;
    private volatile boolean connected;

    private AdbBridge() throws Exception {
        setApi(Build.VERSION.SDK_INT);
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        String alias = "s24_adb_key";
        if (!ks.containsAlias(alias)) {
            KeyPairGenerator gen = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore");
            gen.initialize(new KeyGenParameterSpec.Builder(alias,
                    KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY)
                    .setKeySize(2048)
                    .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
                    .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                    .setCertificateSubject(new javax.security.auth.x500.X500Principal("CN=S24 Performance"))
                    .setCertificateSerialNumber(java.math.BigInteger.ONE)
                    .setCertificateNotBefore(new Date(System.currentTimeMillis() - 86400000L))
                    .setCertificateNotAfter(new Date(System.currentTimeMillis() + 31536000000L))
                    .build());
            gen.generateKeyPair();
        }
        key = (PrivateKey) ks.getKey(alias, null);
        certificate = ks.getCertificate(alias);
    }

    public static synchronized AdbBridge get() throws Exception {
        if (INSTANCE == null) INSTANCE = new AdbBridge();
        return INSTANCE;
    }
    @Override protected PrivateKey getPrivateKey() { return key; }
    @Override protected Certificate getCertificate() { return certificate; }
    @Override protected String getDeviceName() { return "S24 Performance"; }
    public boolean isConnected() { return connected; }

    public void pairAsync(String host, int port, String code, Callback cb) {
        executor.execute(() -> {
            try {
                if (!pair(host, port, code)) throw new IOException("Emparejamiento rechazado");
                cb.finished("Emparejado. Ahora utiliza el puerto de CONEXION ADB, distinto al de emparejamiento.", true);
            } catch (Exception e) { cb.finished(e.toString(), false); }
        });
    }
    public void connectAsync(String host, int port, Callback cb) {
        executor.execute(() -> {
            try {
                if (!connect(host, port)) throw new IOException("Conexión rechazada");
                connected = true;
                cb.finished("ADB conectado", true);
            } catch (Exception e) { connected = false; cb.finished(e.toString(), false); }
        });
    }
    public void executeAsync(String command, Callback cb) {
        executor.execute(() -> {
            try { cb.finished(run(command), true); }
            catch (Exception e) { cb.finished(e.toString(), false); }
        });
    }
    public String run(String command) throws Exception {
        if (!connected) throw new IllegalStateException("Primero conectar ADB");
        try (AdbStream stream = openStream("shell:")) {
            OutputStream out = stream.openOutputStream();
            InputStream in = stream.openInputStream();
            out.write((command + "\nprintf '\\n__S24_EXIT__%s\\n' \"$?\"\nexit\n")
                    .getBytes(StandardCharsets.UTF_8));
            out.flush();
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] bytes = new byte[4096];
            int n;
            while ((n = in.read(bytes)) != -1) {
                b.write(bytes, 0, n);
                if (b.size() > 262144) throw new IOException("Salida excesiva de ADB");
            }
            String result = b.toString("UTF-8");
            if (!result.matches("(?s).*__S24_EXIT__0\\s*(?:.*)?"))
                throw new IOException("Comando no confirmado: " + result);
            return result.replaceAll("(?s)\\n__S24_EXIT__0.*$", "").trim();
        }
    }
}
