package org.omegat.core.team2.encryption;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.logging.Logger;

import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.ComputerSystem;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.software.os.OperatingSystem;

import org.omegat.util.Log;

/**
 * Generates a unique, reproducible machine+user identifier string used as the
 * basis for deriving an AES encryption key for repositories.properties.
 *
 * Mirrors the logic from SaltGenerator: probe each source in strict
 * priority order, log which one was chosen, cache the result for the session.
 */
public final class MachineIdGenerator {

    private static final Logger LOGGER = Logger.getLogger(MachineIdGenerator.class.getName());

    private static String cachedMachineId = null;

    private MachineIdGenerator() {
    }

    /**
     * Returns the reproducible machine+user identifier.
     * Cached after the first call for the JVM session lifetime.
     */
    public static synchronized String getMachineId() {
        if (cachedMachineId != null) {
            return cachedMachineId;
        }

        // Collect all candidate values upfront before any branching
        // to avoid partial-init issues with OSHI
        String osFamily        = "";
        String hardwareUUID    = "";
        String baseboardSerial = "";
        String processorId     = "";

        try {
            SystemInfo si          = new SystemInfo();
            OperatingSystem os     = si.getOperatingSystem();
            HardwareAbstractionLayer hal = si.getHardware();
            ComputerSystem cs      = hal.getComputerSystem();
            CentralProcessor cpu   = hal.getProcessor();

            osFamily        = os.getFamily();
            hardwareUUID    = cs.getHardwareUUID();
            baseboardSerial = cs.getBaseboard().getSerialNumber();
            processorId     = cpu.getProcessorIdentifier().getProcessorID();

            Log.logDebug(LOGGER, "MachineIdGenerator OSHI values - OS: {0}, UUID: {1}, Baseboard: {2}, CPU: {3}",
                    osFamily, hardwareUUID, baseboardSerial, processorId);
        } catch (Exception e) {
            Log.log("MachineIdGenerator: OSHI probe failed: " + e.getMessage());
        }

        // Pre-fetch Linux software ID
        String linuxSoftwareId = fetchLinuxSoftwareId(osFamily);

        Log.logDebug(LOGGER, "MachineIdGenerator: Linux software ID: {0}", linuxSoftwareId);

        // Strict priority — same order as SaltGenerator
        String primaryId;
        String sourceUsed;

        if (isValid(hardwareUUID)) {
            primaryId = hardwareUUID;
            sourceUsed = "Hardware UUID";
        } else if (isValid(baseboardSerial)) {
            primaryId = baseboardSerial;
            sourceUsed = "Baseboard Serial";
        } else if (isValid(linuxSoftwareId)) {
            primaryId = linuxSoftwareId;
            sourceUsed = "Linux Software ID";
        } else if (isValid(processorId)) {
            primaryId = processorId;
            sourceUsed = "Processor ID";
        } else {
            primaryId = System.getProperty("os.arch") + "-" + System.getProperty("user.home");
            sourceUsed = "Fallback (os.arch + user.home)";
        }

        Log.log("MachineIdGenerator: source selected = " + sourceUsed);

        cachedMachineId = primaryId + "-" + System.getProperty("user.name");
        return cachedMachineId;
    }

    /**
     * Reads /etc/machine-id or /var/lib/dbus/machine-id on Linux.
     * Returns empty string on all other platforms or if neither file is readable.
     */
    private static String fetchLinuxSoftwareId(String osFamily) {
        String os = osFamily.toLowerCase();
        boolean isLinux = os.contains("linux") || os.contains("debian")
                || os.contains("ubuntu") || os.contains("nix")
                || os.contains("nux") || os.contains("aix");
        if (!isLinux) {
            return "";
        }
        String[] paths = {"/etc/machine-id", "/var/lib/dbus/machine-id"};
        for (String path : paths) {
            File file = new File(path);
            if (file.exists() && file.canRead()) {
                try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                    String id = reader.readLine();
                    if (isValid(id)) {
                        return id.trim();
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return "";
    }

    private static boolean isValid(String s) {
        return s != null && !s.trim().isEmpty()
                && !s.equalsIgnoreCase("unknown")
                && !s.equalsIgnoreCase("n/a");
    }
}