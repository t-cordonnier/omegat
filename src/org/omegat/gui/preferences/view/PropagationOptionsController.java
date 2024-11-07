/**************************************************************************
 OmegaT - Computer Assisted Translation (CAT) tool
          with fuzzy matching, translation memory, keyword search,
          glossaries, and translation leveraging into updated projects.

 Copyright (C) 2024 Thomas CORDONNIER
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

package org.omegat.gui.preferences.view;

import javax.swing.JComponent;

import org.omegat.gui.preferences.BasePreferencesController;
import org.omegat.util.OStrings;
import org.omegat.util.Preferences;

/**
 * @author Aaron Madlon-Kay
 */
public class PropagationOptionsController extends BasePreferencesController {

    private PropagationOptionsPanel panel;

    @Override
    public JComponent getGui() {
        if (panel == null) {
            initGui();
            initFromPrefs();
        }
        return panel;
    }

    @Override
    public String toString() {
        return OStrings.getString("PREFS_TITLE_PROPAGATION");
    }

    private void initGui() {
        panel = new PropagationOptionsPanel();
    }

    @Override
    protected void initFromPrefs() {
        panel.displayMultipleMatchesCheckBox
                .setSelected(Preferences.isPreferenceDefault(Preferences.PROPAGATION_DISPLAY_MATCHES, true));
        panel.autoDisabledOnlyCheckBox
                .setSelected(Preferences.isPreference(Preferences.PROPAGATION_AUTO_DISABLED_ONLY));
        panel.autoDisabledOnlyCheckBox
                .setEnabled(panel.displayMultipleMatchesCheckBox.isSelected());
    }

    @Override
    public void restoreDefaults() {
        panel.displayMultipleMatchesCheckBox.setSelected(false);
        panel.autoDisabledOnlyCheckBox.setSelected(false);
    }

    @Override
    public void persist() {
        Preferences.setPreference(Preferences.PROPAGATION_DISPLAY_MATCHES,
                panel.displayMultipleMatchesCheckBox.isSelected());
        Preferences.setPreference(Preferences.PROPAGATION_AUTO_DISABLED_ONLY,
                panel.autoDisabledOnlyCheckBox.isSelected());
    }
}
