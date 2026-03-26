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

package org.omegat.core.team2;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

import org.omegat.core.team2.impl.TeamUtils;

public class EncryptionTest {

    @Test
    public void testEncodeDecode() throws Exception {
        String test = "this is a test";
        test = TeamUtils.encodePassword(test);
        System.out.println("Encrypted: " + test);
        test = TeamUtils.decodePassword(test);
        assertEquals(test, "this is a test");
    }
    
    @Test
	public void testRetroCompatible() throws Exception {
        // Pure Base 64, without AES 
        String test = "dGhpcyBpcyBhIHRlc3QK";
        assertEquals(TeamUtils.decodePassword(test), "this is a test");        
	}
	
}
