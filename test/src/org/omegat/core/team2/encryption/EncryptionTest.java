/**************************************************************************
 OmegaT - Computer Assisted Translation (CAT) tool
 with fuzzy matching, translation memory, keyword search,
 glossaries, and translation leveraging into updated projects.

 Copyright (C) 2026 Thomas CORDONNIER
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

package org.omegat.core.team2.encryption;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;


public class EncryptionTest {

    @Test
    public void testEncodeDecode() throws Exception {
        String test = "this is a test";
        assertFalse(TeamSettingsEncryptor.isEncrypted(test.getBytes("UTF-8")));
        byte[] crypt = TeamSettingsEncryptor.encrypt(test.getBytes("UTF-8"));
        System.out.println("Encrypted: " + new String(crypt, "UTF-8"));
        assertTrue(TeamSettingsEncryptor.isEncrypted(crypt));
        crypt = TeamSettingsEncryptor.decrypt(crypt);
        assertEquals(new String(crypt, "UTF-8"), "this is a test");
    }
    
    @Test
    public void testMachineId() {
        String machineId = MachineIdGenerator.getMachineIdNoCache();
        // Assert that calling twice this method gives same result
        assertEquals(machineId, MachineIdGenerator.getMachineIdNoCache());
    }
    
	
}
