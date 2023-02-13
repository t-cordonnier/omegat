/**************************************************************************
 OmegaT - Computer Assisted Translation (CAT) tool
          with fuzzy matching, translation memory, keyword search,
          glossaries, and translation leveraging into updated projects.

 Copyright (C) 2022-2023 Thomas CORDONNIER
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
package org.omegat.core.pack;

import java.io.File;

import java.util.Set;
import java.util.HashSet;

import org.omegat.core.data.ProjectProperties;

/**
 * Interface for a new package format
 *
 * @author Thomas CORDONNIER
 */
public interface IPackageFormat {
    /** Name to be displayed in the drop-down list */
    String getPackFormatName();
    
    /** Accepted file name extensions **/
    String[] extensions();
    
    /** Put here all what is needed before opening the file dialog **/
    void init();
    
    // ----------------- Export ---------------------
    
    /** Default file for exportation (the user can modify through file dialog) **/
    File defaultExportFile(boolean deleteProject);
    
    /** Do we generate target before creating a package? **/
    boolean generateTargetBeforePackageCreation();
    
    /** Do we display the containing folder on desktop after creation **/
    boolean openFolderAfterCreation();
    
    /** Create the package **/
    void createPackage(final File omtZip, final ProjectProperties props) throws Exception;
    
    // ----------------- Import ---------------------
    
    /** Do we delete package after import? **/
    boolean deletePackageAfterImport();
    
    /** Do we open package after import? **/
    boolean openProjectAfterImport();
    
    /** Default location for project, depending on package file **/
    File defaultImportDirectory(File packFile);

    /** Do we ask for import location, or keep default? **/
    boolean askForImportDirectory();
    
    /** Default location for project, depending on package file **/
    File extractFromPack(File packFile, File destFile) throws Exception;

    // -----------------------------------------------------------------
    
    // Static collection where all package formats will register themselves
    public static final Set<IPackageFormat> FORMATS = new HashSet<>();
}
