/**************************************************************************
 OmegaT - Computer Assisted Translation (CAT) tool
          with fuzzy matching, translation memory, keyword search,
          glossaries, and translation leveraging into updated projects.

 Copyright (C) 2026 Aaron Madlon-Kay
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

package org.omegat.util.gui;

import javax.swing.event.DocumentListener;
import javax.swing.event.DocumentEvent;

import java.util.regex.Pattern;

/**
 * Document listener which trims text inserted via Copy/Paste
 * @author Thomas CORDONNIER
 *
 */
public class TrimmerListener implements DocumentListener {
    public void changedUpdate(DocumentEvent e) {}
    public void removeUpdate(DocumentEvent e) {}
    
    Pattern START_SPACE = Pattern.compile("\\A\\s"), END_SPACE = Pattern.compile ("\\s\\z");
    
    public void insertUpdate(DocumentEvent e) {
        javax.swing.SwingUtilities.invokeLater(() -> {
            try {
                javax.swing.text.Document doc = e.getDocument();
                while (START_SPACE.matcher(doc.getText(0,1)).matches()) doc.remove(0,1);
                while (END_SPACE.matcher(doc.getText(doc.getLength() - 1, 1)).matches()) doc.remove(doc.getLength() - 1, 1);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });
    }
}
