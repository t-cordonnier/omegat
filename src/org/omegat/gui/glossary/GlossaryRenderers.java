/**************************************************************************
 OmegaT - Computer Assisted Translation (CAT) tool
          with fuzzy matching, translation memory, keyword search,
          glossaries, and translation leveraging into updated projects.

 Copyright (C) 2020 Aaron Madlon-Kay
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

package org.omegat.gui.glossary;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.omegat.util.Preferences;

/**
 * A class for aggregating glossary renderers. Extensions and scripts can add their
 * providers here with {@link #addGlossaryRenderer(IGlossaryRenderer)}.
 *
 * @author Aaron Madlon-Kay
 *
 */
public class GlossaryRenderers {
    private static final List<IGlossaryRenderer> GLOSSARY_RENDERERS = new ArrayList<>();
    static {
        addGlossaryRenderer(new DefaultGlossaryRenderer());
        addGlossaryRenderer(new DictionaryGlossaryRenderer());
    }

    private GlossaryRenderers() {
    }

    public static List<IGlossaryRenderer> getAll() {
        return Collections.unmodifiableList(GLOSSARY_RENDERERS);
    }

    public static void addGlossaryRenderer(IGlossaryRenderer renderer) {
        GLOSSARY_RENDERERS.add(renderer);
    }

    public static void setPreferredGlossaryRenderer(IGlossaryRenderer renderer) {
        Preferences.setPreference(Preferences.GLOSSARY_LAYOUT, renderer.getId());
    }

    public static IGlossaryRenderer getPreferredGlossaryRenderer() {
        String preferredId = Preferences.getPreference(Preferences.GLOSSARY_LAYOUT);
        for (IGlossaryRenderer renderer : GLOSSARY_RENDERERS) {
            if (renderer.getId().equals(preferredId)) {
                return renderer;
            }
        }
        // Preferred renderer not found
        IGlossaryRenderer renderer = new DefaultGlossaryRenderer();
        if (preferredId.isEmpty()) {
            // Preferred renderer was not set
            setPreferredGlossaryRenderer(renderer);
        }
        return renderer;
    }
}
