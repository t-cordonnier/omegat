/**************************************************************************
 OmegaT - Computer Assisted Translation (CAT) tool
          with fuzzy matching, translation memory, keyword search,
          glossaries, and translation leveraging into updated projects.

 Copyright (C) 2016 Alex Buloichik
               Home page: http://www.omegat.org/
               Support center: https://omegat.org/support

 This file is part of OmegaT.

 OmegaT is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 OmegaT is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.
 **************************************************************************/

package org.omegat.core.team2.impl;

import oshi.SystemInfo;
import oshi.hardware.ComputerSystem;
import oshi.hardware.CentralProcessor;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.software.os.OperatingSystem;

import java.security.NoSuchAlgorithmException;
import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.BadPaddingException;
import java.security.InvalidAlgorithmParameterException;
import java.security.spec.InvalidKeySpecException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.io.File;
import java.io.FileReader;
import java.io.BufferedReader;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;


/**
 * Some utility methods for team code.
 *
 * @author Alex Buloichik (alex73mail@gmail.com)
 * @author Thomas CORDONNIER, Kos Ivantsov
 */
public final class TeamUtils {

    private TeamUtils() {
    }
    
    // Helper to check if a string successfully retrieved a value and is not an OSHI "unknown" placeholder
    private static boolean isValid(String s) {
        return s != null && !s.trim().isEmpty() && !s.equalsIgnoreCase("unknown");
    }
    
    /** Hardware-dependent key, based on Kos's algorithm **/
    public static byte[] oshiKey() throws NoSuchAlgorithmException, UnsupportedEncodingException {
        SystemInfo systemInfo = new SystemInfo();
        OperatingSystem os = systemInfo.getOperatingSystem();
        HardwareAbstractionLayer hal = systemInfo.getHardware();
        ComputerSystem computerSystem = hal.getComputerSystem();
        CentralProcessor processor = hal.getProcessor();
        

        // Gather raw data
        String osFamily = os.getFamily();
        String hardwareUUID = computerSystem.getHardwareUUID();
        String baseboardSerial = computerSystem.getBaseboard().getSerialNumber();
        String processorId = processor.getProcessorIdentifier().getProcessorID();
        String userName = System.getProperty("user.name");

        // Fetch Linux Software ID for the output display
        String linuxSoftwareId = "N/A";
        if (osFamily.toLowerCase().contains("linux") || osFamily.toLowerCase().contains("debian") || osFamily.toLowerCase().contains("ubuntu")) {
            String[] paths = {"/etc/machine-id", "/var/lib/dbus/machine-id"};
            for (String path : paths) {
                File file = new File(path);
                if (file.exists() && file.canRead()) {
                    try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                        String id = reader.readLine();
                        if (isValid(id)) {
                            linuxSoftwareId = id.trim();
                            break;
                        }
                    } catch (Exception ignored) {}
                }
            }
        }

        // Determine the best available identifier
        String primaryId = null;

        if (isValid(hardwareUUID)) {
            primaryId = hardwareUUID;
        } else if (isValid(baseboardSerial)) {
            primaryId = baseboardSerial;
        } else if (!linuxSoftwareId.equals("N/A")) {
            primaryId = linuxSoftwareId;
        } else if (isValid(processorId)) {
            primaryId = processorId;
        } else {
            primaryId = System.getProperty("os.arch") + "-" + System.getProperty("user.home");
        }

        // Generate the raw Salt String and hash it
        String rawSalt = primaryId + "-" + userName;
        
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        return sha.digest(rawSalt.getBytes("UTF-8"));            
    }
    
    private static final String OT_PRIVATE_KEY = "sodpfizeriu";  // random-generated, do not change
    
    // Source: https://howtodoinjava.com/java/java-security/aes-256-encryption-decryption/
    private static final int KEY_LENGTH = 256;
    private static final int ITERATION_COUNT = 65536;

    public static String encodePassword(String pass) throws NoSuchAlgorithmException, UnsupportedEncodingException, BadPaddingException,
        InvalidKeyException, IllegalBlockSizeException, InvalidKeySpecException, NoSuchPaddingException, InvalidAlgorithmParameterException {
        
        if (pass == null) return null;
        //return Base64.getEncoder().encodeToString(pass.getBytes("UTF-8"));
        
        SecureRandom secureRandom = new SecureRandom();
        byte[] iv = new byte[16];
        secureRandom.nextBytes(iv);
        IvParameterSpec ivspec = new IvParameterSpec(iv);

        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec spec = new PBEKeySpec(OT_PRIVATE_KEY.toCharArray(), oshiKey(), ITERATION_COUNT, KEY_LENGTH);
        SecretKey tmp = factory.generateSecret(spec);
        SecretKeySpec secretKeySpec = new SecretKeySpec(tmp.getEncoded(), "AES");

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, ivspec);

        byte[] cipherText = cipher.doFinal(pass.getBytes("UTF-8"));
        byte[] encryptedData = new byte[iv.length + cipherText.length];
        System.arraycopy(iv, 0, encryptedData, 0, iv.length);
        System.arraycopy(cipherText, 0, encryptedData, iv.length, cipherText.length);
        // *** to indicate that we use AES (without, it is supposed to be simple Base64 encryption as in older releases of OmegaT)
        return "***" + Base64.getEncoder().encodeToString(encryptedData);   
    }

    public static String decodePassword(String pass) throws NoSuchAlgorithmException, UnsupportedEncodingException, InvalidKeySpecException, 
        IllegalBlockSizeException, InvalidKeyException, BadPaddingException, NoSuchPaddingException, InvalidAlgorithmParameterException {
        
        if (pass == null) return null;
        if (pass.startsWith("***")) {
            byte[] encryptedData = Base64.getDecoder().decode(pass = pass.substring(3));
            byte[] iv = new byte[16];
            System.arraycopy(encryptedData, 0, iv, 0, iv.length);
            IvParameterSpec ivspec = new IvParameterSpec(iv);

            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            KeySpec spec = new PBEKeySpec(OT_PRIVATE_KEY.toCharArray(), oshiKey(), ITERATION_COUNT, KEY_LENGTH);
            SecretKey tmp = factory.generateSecret(spec);
            SecretKeySpec secretKeySpec = new SecretKeySpec(tmp.getEncoded(), "AES");

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, ivspec);

            byte[] cipherText = new byte[encryptedData.length - 16];
            System.arraycopy(encryptedData, 16, cipherText, 0, cipherText.length);

            byte[] decryptedText = cipher.doFinal(cipherText);
            return new String(decryptedText, "UTF-8");            
        } else {    // compatibility with other versions of OmegaT      */  
            byte[] data = Base64.getDecoder().decode(pass);
            return new String(data, StandardCharsets.UTF_8);
        }
    }
}
