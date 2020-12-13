package de.aaschmid.taskwarrior.ssl;

import static java.util.Objects.requireNonNull;

import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

public class SslContextFactory {

    private static final String DEFAULT_PROTOCOL = "TLS";

    public static SSLContext getInstance(String protocol, KeyStore keyStore, String keyStorePassword) {
        requireNonNull(protocol, "'protocol' must not be null.");
        requireNonNull(keyStore, "'keyStore' must not be null.");
        requireNonNull(keyStorePassword, "'keyStorePassword' must not be null.");

        SSLContext sslContext;
        try {
            sslContext = SSLContext.getInstance(protocol);
        } catch (NoSuchAlgorithmException e) {
            throw new TaskwarriorSslContextException(e, "Cannot get SSL context for protocol '%s': %s", protocol, e.getMessage());
        }
        try {
            sslContext.init(loadKeyMaterial(keyStore, keyStorePassword), loadTrustMaterial(keyStore), null);
        } catch (KeyManagementException | UnrecoverableKeyException | NoSuchAlgorithmException | KeyStoreException e) {
            throw new TaskwarriorSslContextException(e, "Could not init ssl context: %s", e.getMessage());
        }
        return sslContext;
    }

    public static SSLContext getInstance(KeyStore keyStore, String keyStorePassword) {
        return getInstance(DEFAULT_PROTOCOL, keyStore, keyStorePassword);
    }

    private static KeyManager[] loadKeyMaterial(KeyStore keystore, String keyStorePassword)
            throws NoSuchAlgorithmException, UnrecoverableKeyException, KeyStoreException {
        KeyManagerFactory result = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        result.init(keystore, keyStorePassword.toCharArray());
        return result.getKeyManagers();
    }

    private static TrustManager[] loadTrustMaterial(KeyStore truststore) throws NoSuchAlgorithmException, KeyStoreException {
        TrustManagerFactory result = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        result.init(truststore);
        return result.getTrustManagers();
    }
}
