package org.omegat.core.team2;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.omegat.core.team2.encryption.TeamSettingsEncryptor;
import org.omegat.util.Log;
import org.omegat.util.StaticUtils;

public final class TeamSettings {

    private TeamSettings() {
    }

    private static File configFile;

    // In-memory cache: loaded once on first access and kept authoritative
    // for the entire OmegaT session. All writes go through persistToDisk()
    // which keeps the cache and the file in sync. We never re-read the file
    // after the initial load — external modifications (e.g. file sync tools)
    // are intentionally ignored to prevent binary-read corruption.
    private static Properties cachedProperties = null;

    private static synchronized File getConfigFile() {
        if (configFile == null) {
            configFile = new File(StaticUtils.getConfigDir(), "repositories.properties");
        }
        return configFile;
    }

    // Returns the in-memory properties, loading and decrypting from disk only
    // on the very first call of the session.
    private static synchronized Properties getProperties() throws Exception {
        if (cachedProperties != null) {
            return cachedProperties;
        }

        // First access: load from disk.
        cachedProperties = new Properties();
        File f = getConfigFile();

        if (!f.exists()) {
            return cachedProperties; // fresh start, nothing to load
        }

        byte[] fileBytes = Files.readAllBytes(f.toPath());

        // Use the magic header to reliably distinguish encrypted from plain-text.
        if (TeamSettingsEncryptor.isEncrypted(fileBytes)) {
            try {
                byte[] decrypted = TeamSettingsEncryptor.GLOBAL_ENCRYPTOR.decrypt(fileBytes);
                cachedProperties.load(new ByteArrayInputStream(decrypted));
                Log.log("TeamSettings: loaded " + cachedProperties.size()
                        + " keys from encrypted file.");
            } catch (Exception e) {
                // Has magic header but failed to decrypt (wrong machine? corrupt).
                // Start empty rather than crash or corrupt further.
                Log.log("TeamSettings: decryption failed (" + e.getClass().getSimpleName()
                        + ") - starting with empty credentials.");
                cachedProperties = new Properties();
            }
            return cachedProperties;
        }

        // No magic header: plain-text file from an older installation.
        // Load and immediately re-save encrypted so this path is taken only once.
        Log.log("TeamSettings: plain-text file detected - migrating to encrypted format.");
        cachedProperties.load(new ByteArrayInputStream(fileBytes));
        persistToDisk();
        return cachedProperties;
    }

    // Serialises cachedProperties, encrypts, and atomically replaces the file on disk.
    private static void persistToDisk() throws Exception {
        File f    = getConfigFile();
        File fNew = new File(f.getParentFile(), "repositories.properties.new");

        f.getParentFile().mkdirs();

        // Serialise to bytes before touching the file.
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        cachedProperties.store(baos, null);
        byte[] plainBytes = baos.toByteArray();

        Log.log("TeamSettings: persisting " + cachedProperties.size()
                + " keys, plaintext size = " + plainBytes.length + " bytes.");

        byte[] encrypted = TeamSettingsEncryptor.GLOBAL_ENCRYPTOR.encrypt(plainBytes);

        // Write to temp file, then atomically rename to avoid partial reads.
        try (FileOutputStream out = new FileOutputStream(fNew)) {
            out.write(encrypted);
            out.flush();
        }

        Files.move(fNew.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    // -------------------------------------------------------------------------
    // Public API — all methods synchronised to serialise cache access.
    // -------------------------------------------------------------------------

    public static synchronized Set<Object> listKeys() {
        try {
            return getProperties().keySet();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public static synchronized String get(String key) {
        try {
            return getProperties().getProperty(key);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    // Sets or removes a single key, then persists to disk.
    // Pass {@code null} as {@code newValue} to remove the key.
    // Prefer {@link #setAll(Map)} when saving multiple keys at once
    // (e.g. username + password) to avoid intermediate partial writes.
    public static synchronized void set(String key, String newValue) {
        try {
            Properties p = getProperties();
            if (newValue != null) {
                p.setProperty(key, newValue);
            } else {
                p.remove(key);
            }
            persistToDisk();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    // Sets or removes multiple keys in a single atomic disk write.
    // Pass {@code null} as a value to remove that key.
    // Use this instead of multiple set() calls when saving credentials
    // (username + password) to avoid a window where only one key is on disk.
    public static synchronized void setAll(Map<String, String> entries) {
        try {
            Properties p = getProperties();
            for (Map.Entry<String, String> e : entries.entrySet()) {
                if (e.getValue() != null) {
                    p.setProperty(e.getKey(), e.getValue());
                } else {
                    p.remove(e.getKey());
                }
            }
            persistToDisk();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
