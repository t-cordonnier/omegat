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

package org.omegat.core.team2;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.FileUtils;

import org.omegat.core.team2.impl.TeamUtils;
import org.omegat.util.StaticUtils;
import org.omegat.util.Log;

/**
 * Class for read/save repository-specific settings in the ~/.omegat/ directory. <br/>
 * Warning: this class manages calls to encryption methods from TeamUtils, deciding when it is necessary <br/>
 * Do not encode/decode values before calling TeamSettings.get nor TeamSettings.set!!!
 *
 * @author Alex Buloichik (alex73mail@gmail.com)
 */
public final class TeamSettings {

    private TeamSettings() {
    }

    private static File configFile;

    private static synchronized File getConfigFile() {
        if (configFile == null) {
            configFile = new File(StaticUtils.getConfigDir(), "repositories.properties");
        }
        return configFile;
    }
    
    /** 
     * Whenever the value should be stored as encrypted or not
     * Private, because that should remain transparent to users
     **/
    private static boolean needsEncryption(String key) {
        return key.endsWith("!password");
    }
    
    // Should be called at first load of the class
    static {
        synchronized (TeamSettings.class) {
            // Try to encrypt all passwords
            try {
                Properties props = new Properties(); File fOri = getConfigFile();
                if (fOri.exists()) {
                    try (FileInputStream in = new FileInputStream(fOri)) {
                        props.load(in);
                    }
                    int change = 0;
                    for(Map.Entry<Object, Object> e : props.entrySet()) 
                        if (needsEncryption(e.getKey().toString()))
                            if (! e.getValue().toString().startsWith("***")) {
                                props.put(e.getKey().toString(), TeamUtils.encodePassword(TeamUtils.decodePassword(e.getValue().toString())));
                                change++;
                            }
                    if (change > 0) {
                        File fNew = new File(getConfigFile().getAbsolutePath() + ".new");
                        try (FileOutputStream out = new FileOutputStream(fNew)) {            
                            props.store(out, null);
                        }
                        fOri.delete(); FileUtils.moveFile(fNew, fOri);
                        Log.log("TeamSettings: " + change + " passwords reencrypted");
                    }
                    else Log.log("TeamSettings: no password requires re-encryption");
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    public static synchronized Set<Object> listKeys() {
        try {
            Properties p = new Properties();
            if (getConfigFile().exists()) {
                FileInputStream in = new FileInputStream(getConfigFile());
                try {
                    p.load(in);
                } finally {
                    in.close();
                }
            }
            return p.keySet();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Get setting directly readable by caller - decrypted if necessary
     */
    public static synchronized String get(String key) {
        try {
            Properties p = new Properties();
            if (getConfigFile().exists()) {
                FileInputStream in = new FileInputStream(getConfigFile());
                try {
                    p.load(in);
                } finally {
                    in.close();
                }
            }
            if (needsEncryption(key)) return TeamUtils.decodePassword(p.getProperty(key));
            else return p.getProperty(key);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Update setting. Encrypts it if necessary  - do not use set(key, encode(value))!!!
     */
    public static synchronized void set(String key, String newValue) {
        try {
            Properties p = new Properties();
            File f = getConfigFile();
            File fNew = new File(getConfigFile().getAbsolutePath() + ".new");
            if (f.exists()) {
                FileInputStream in = new FileInputStream(f);
                try {
                    p.load(in);
                } finally {
                    in.close();
                }
            } else {
                f.getParentFile().mkdirs();
            }
            if (newValue != null) {
                if (needsEncryption(key)) newValue = TeamUtils.encodePassword(newValue);
                p.setProperty(key, newValue);
            } else {
                p.remove(key);
            }
            FileOutputStream out = new FileOutputStream(fNew);
            try {
                p.store(out, null);
            } finally {
                out.close();
            }
            f.delete();
            FileUtils.moveFile(fNew, f);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
