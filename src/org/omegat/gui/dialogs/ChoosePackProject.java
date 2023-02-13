/**************************************************************************
 OmegaT - Computer Assisted Translation (CAT) tool
          with fuzzy matching, translation memory, keyword search,
          glossaries, and translation leveraging into updated projects.

 Copyright (C) 2016 Alex Buloichik
               2022-2023 Thomas Cordonnier
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

package org.omegat.gui.dialogs;

import java.io.File;
import java.util.Locale;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileFilter;

import org.omegat.core.pack.IPackageFormat;
import org.omegat.util.OStrings;
import org.omegat.util.Preferences;

@SuppressWarnings("serial")
public class ChoosePackProject extends JFileChooser {
    private boolean deleteProject;

    public ChoosePackProject(final boolean forExport, final boolean deleteProject) {
        super(Preferences.getPreference(Preferences.CURRENT_FOLDER));

        setMultiSelectionEnabled(false);
        setFileHidingEnabled(true);
        setFileSelectionMode(FILES_ONLY);
        setDialogTitle(OStrings.getString("PP_PACK_OPEN"));
        setAcceptAllFileFilterUsed(false);
        for (IPackageFormat fmt: IPackageFormat.FORMATS) {
            addChoosableFileFilter(new PackFormatFilter(fmt));
        }
        if (forExport) {
            addPropertyChangeListener(ev -> {
                if (ev.getPropertyName().equals(FILE_FILTER_CHANGED_PROPERTY)) {
                    setSelectedFile(selectedFormat().defaultExportFile(deleteProject));
                }
            });
        }
    }
    
    class PackFormatFilter extends FileFilter {
        private final IPackageFormat format;
        
        public PackFormatFilter(IPackageFormat format) {
            this.format = format;
        }
    
        public boolean accept(File f) {
            if (f.isDirectory()) {
                return true;
            }
            for (String ext: format.extensions()) {
                if (f.getName().endsWith(ext)) return true;
            }
            return false;
        }
        
        public String getDescription() {
            return format.getPackFormatName();
        }
        
        public IPackageFormat format() {
            return format;
        }
    }
    
    public IPackageFormat selectedFormat() {
        return ((PackFormatFilter) getFileFilter()).format();
    }
}
