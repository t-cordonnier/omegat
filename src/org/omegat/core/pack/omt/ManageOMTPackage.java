/**************************************************************************
 OmegaT Plugin - OMT Package Manager

 Copyright (C) 2019 Briac Pilpré
 Home page: http://www.omegat.org/
 Support center: http://groups.yahoo.com/group/OmegaT/

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program. If not, see <http://www.gnu.org/licenses/>.
 **************************************************************************/
package org.omegat.core.pack.omt;

import static org.omegat.core.Core.getMainWindow;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;

import javax.swing.JOptionPane;

import java.util.Arrays;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.omegat.core.Core;
import org.omegat.core.data.ProjectProperties;
import org.omegat.gui.scripting.ScriptRunner;
import org.omegat.gui.scripting.IScriptLogger;
import org.omegat.gui.scripting.ScriptItem;
import org.omegat.util.FileUtil;
import org.omegat.util.Log;
import org.omegat.util.OConsts;
import org.omegat.util.OStrings;
import org.omegat.util.Preferences;
import org.omegat.util.ProjectFileStorage;
import org.omegat.util.StaticUtils;

import org.apache.commons.io.FileUtils;

public class ManageOMTPackage {
    public static final String PLUGIN_VERSION = ManageOMTPackage.class.getPackage().getImplementationVersion();

    public static final String OMT_EXTENSION = ".omt";
    protected static final String OMT_PACKER_LOGNAME = "omt-packer.log";
    public static final String CONFIG_FILE = "omt-package-config.properties";
    public static final String IGNORE_FILE = ".empty";

    public static final String PROPERTY_EXCLUDE = "exclude-pattern";
    public static final String DEFAULT_EXCLUDE = "\\.(zip|bak|omt|lck)$;\\.repositories";
    public static final String PROPERTY_OPEN_DIR = "open-directory-after-export";
    public static final String PROPERTY_GENERATE_TARGET = "generate-target-files";
    public static final String PROPERTY_PROMPT_DELETE_IMPORT = "prompt-remove-omt-after-import";
    public static final String PROPERTY_POST_PACKAGE_SCRIPT = "post-package-script";

    protected static final Logger LOGGER = Logger.getLogger(ManageOMTPackage.class.getName());

    private static FileWriter fhandler;

    public static Properties pluginProps = new Properties();

    public static void loadPluginProps() {
        loadPluginProps(new File(StaticUtils.getConfigDir(), CONFIG_FILE));
    }

    private static void loadPluginProps(File propFile) {
        pluginProps = new Properties();

        if (!propFile.exists()) {
            Log.logDebug(LOGGER, "No app plugin properties [{0}], creating one...", propFile.getAbsolutePath());
            try {
                FileUtils.copyInputStreamToFile(ManageOMTPackage.class.getResourceAsStream("/" + CONFIG_FILE),
                        propFile);
            } catch (IOException e) {
                Log.log(e);
                return;
            }
        }
        try (FileInputStream inStream = new FileInputStream(propFile)) {
            pluginProps.load(inStream);
            Log.logDebug(LOGGER, "OMT App Plugin Properties");
            if (LOGGER.isLoggable(Level.FINE)) {
                pluginProps.list(System.out);
            }
        } catch (IOException e) {
            Log.log(String.format("Could not load plugin property file \"%s\"", propFile.getAbsolutePath()));
        }

        if (Core.getProject() == null || !Core.getProject().isProjectLoaded()) {
            // No project specific properties!
            return;
        }

        propFile = new File(Core.getProject().getProjectProperties().getProjectRootDir(), CONFIG_FILE);
        if (propFile.exists()) {
            try (FileInputStream inStream = new FileInputStream(propFile)) {
                pluginProps.load(inStream);
                Log.logDebug(LOGGER, "OMT Project Plugin Properties");
                pluginProps.list(System.out);
            } catch (IOException e) {
                Log.log(String.format("Could not load project plugin property file \"%s\"",
                        propFile.getAbsolutePath()));
            }
        } else {
            Log.logDebug(LOGGER, "No project plugin properties [{0}]", propFile.getAbsolutePath());
        }
    }

    public static void createOmt(final File omtZip, final ProjectProperties props) throws Exception {
        Path path = Paths.get(props.getProjectRootDir().getAbsolutePath());
        if (!Files.isDirectory(path)) {
            throw new IllegalArgumentException("Path \"" + path + "\" must be a directory.");
        }

        List<String> listExcludes = Arrays
                .asList(pluginProps.getProperty(PROPERTY_EXCLUDE, DEFAULT_EXCLUDE).split(";"));

        DirectoryStream.Filter<Path> filter = new DirectoryFilter(path, listExcludes);

        String logFile = props.getProjectInternal() + OMT_PACKER_LOGNAME;
        fhandler = new FileWriter(logFile, true);

        try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(omtZip))) {
            String username = Preferences.getPreferenceDefault(Preferences.TEAM_AUTHOR,
                    System.getProperty("user.name"));
            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());

            omtPackLog("------------------------------------");
            omtPackLog(String.format("Packing timestamp: %s", timestamp));
            omtPackLog(String.format("OMT plugin version: %s", PLUGIN_VERSION));
            omtPackLog(String.format("User ID: \"%s\"", username));
            omtPackLog(String.format("Project name: [%s]", path));
            omtPackLog(String.format("Package name: [%s]", omtZip.getAbsolutePath()));

            int addedFiles = 0;
            try (ZipOutputStream out = new ZipOutputStream(bos)) {
                addedFiles = addZipDir(out, null, path, props, filter);

                omtPackLog(String.format("Added %s files to the Zip.", addedFiles));
                fhandler.close();

                // Add logfile
                out.putNextEntry(new ZipEntry(OConsts.DEFAULT_INTERNAL + '/' + OMT_PACKER_LOGNAME));
                Files.copy(Paths.get(logFile), out);
                out.closeEntry();
            }

        }

        String postPackageScript = pluginProps.getProperty(PROPERTY_POST_PACKAGE_SCRIPT);
        if (postPackageScript != null) {
            runScript(new File(Preferences.getPreference(Preferences.SCRIPTS_DIRECTORY), postPackageScript));
        }

        JOptionPane.showMessageDialog(getMainWindow().getApplicationFrame(),
                OStrings.getString("OMT_DIALOG_OVERWRITE_PACKAGE_CREATED"),
                OStrings.getString("OMT_DIALOG_OVERWRITE_PACKAGE_CREATED_TITLE"), JOptionPane.INFORMATION_MESSAGE);
    }

    private static void runScript(File scriptFile) {
        if (scriptFile.isFile() && scriptFile.exists()) {
            HashMap<String, Object> bindings = new HashMap<>();
            bindings.put("omtPackageFile", scriptFile);

            bindings.put(ScriptRunner.VAR_CONSOLE, new IScriptLogger() {
                @Override
                public void print(Object o) {
                    Log.log(o.toString());
                }

                @Override
                public void println(Object o) {
                    Log.log(o.toString());
                }

                @Override
                public void clear() {
                    /* empty */
                }
            });

            try {
                String result = ScriptRunner.executeScript(new ScriptItem(scriptFile), bindings);
                Log.log(result);
            } catch (Exception ex) {
                Log.log(ex);
            }
        } else {
            Log.log(String.format("No script file \"%s\".", scriptFile.getAbsolutePath()));
        }
    }

    /**
     * It creates project internals from OMT zip file.
     */
    public static File extractFromOmt(File omtFile) throws IOException {
        String omtName = omtFile.getName().replaceAll("\\" + OMT_EXTENSION + "$", "");

        // Check if we're inside a project folder
        File projectDir = new File(omtFile.getParent(), OConsts.FILE_PROJECT);

        try (ZipFile zip = new ZipFile(omtFile)) {

            ZipEntry e = zip.getEntry(OConsts.FILE_PROJECT);
            if (e == null) {
                throw new IOException(OStrings.getString("OMT_INVALID_PACKAGE"));
            }

            Log.log(String.format("Checking for project file \"%s\": %s", projectDir.getAbsolutePath(),
                    projectDir.exists()));

            if (projectDir.exists()) {
                Log.log(OStrings.getString("OMT_UPDATE_PACKAGE"));
                projectDir = omtFile.getParentFile();
            } else {
                Log.log(OStrings.getString("OMT_NEW_PACKAGE"));
                projectDir = new File(omtFile.getParentFile(), omtName);
                projectDir.mkdirs();
            }

            for (Enumeration<? extends ZipEntry> en = zip.entries(); en.hasMoreElements();) {
                e = en.nextElement();

                File outFile = new File(projectDir, e.getName());

                if (outFile.getName().equals(OConsts.STATUS_EXTENSION)
                        && outFile.getParentFile().getName().equals(OConsts.DEFAULT_INTERNAL) && outFile.exists()) {
                    // Maybe not overwrite project_save.tmx if it already exists? Ask the user?
                //@formatter:off
                int overwriteSave = JOptionPane.showConfirmDialog(
                        getMainWindow().getApplicationFrame(),
                        OStrings.getString("OMT_DIALOG_OVERWRITE_PROJECT_SAVE"),
                        OStrings.getString("OMT_DIALOG_OVERWRITE_PROJECT_SAVE_TITLE"),
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.QUESTION_MESSAGE);
                //@formatter:on

                    if (overwriteSave == 0) {
                        // Make a backup even if the user want to overwrite, to be on the safe side.
                        Log.log("Overwriting project_save.tmx");
                        final File f = new File(new File(projectDir, OConsts.DEFAULT_INTERNAL),
                                OConsts.STATUS_EXTENSION);
                        Log.log(String.format("Backuping project file \"%s\"", f.getAbsolutePath()));
                        FileUtil.backupFile(f);
                    } else {
                        Log.log("Skipping project_save.tmx");
                        continue;
                    }
                }

                if (e.isDirectory()) {
                    outFile.mkdirs();
                } else {
                    if (outFile.getName().equals(IGNORE_FILE)) {
                        outFile.getParentFile().mkdirs();
                        continue;
                    }
                    try (InputStream in = zip.getInputStream(e)) {
                        try {
                            org.apache.commons.io.FileUtils.copyInputStreamToFile(in, outFile);
                        } catch (IOException ex) {
                            Log.log(String.format("Error unzipping file \"%s\": %s", outFile, ex.getMessage()));
                        }
                    }
                }
            }

        }

        return projectDir;
    }

    private static int addZipDir(final ZipOutputStream out, final Path root, final Path dir,
            final ProjectProperties props, DirectoryStream.Filter<Path> filter) throws IOException {
        int addedFiles = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, filter)) {
            for (Path child : stream) {
                final Path childPath = child.getFileName();

                // Skip projects inside projects
                if (Files.isDirectory(child) && new File(child.toFile(), OConsts.FILE_PROJECT).exists()) {
                    omtPackLog(String.format("The directory \"%s\" appears to be an OmegaT project, we'll skip it.",
                            child.toFile().getAbsolutePath()));
                    continue;
                }

                if (root == null && childPath.endsWith(OConsts.FILE_PROJECT)) {
                    // Special case - when a project is opened, the project file is locked and
                    // can't be copied directly. To avoid this, we make a temp copy.
                    // We name it with a .bak extension to make sure it's not included in the
                    // package.
                    File tmpProjectFile = File.createTempFile("omt", OConsts.BACKUP_EXTENSION,
                            props.getProjectRootDir());
                    try {
                        ProjectFileStorage.writeProjectFile(props, tmpProjectFile);
                    } catch (Exception e) {
                        throw new IOException(e);
                    }
                    omtPackLog(String.format("addZipDir\tproject\t[%s]", OConsts.FILE_PROJECT));
                    out.putNextEntry(new ZipEntry(OConsts.FILE_PROJECT));
                    Files.copy(Paths.get(tmpProjectFile.getAbsolutePath()), out);
                    addedFiles++;
                    out.closeEntry();
                    boolean isTmpDeleted = tmpProjectFile.delete();
                    if (!isTmpDeleted) {
                        Log.log(String.format("Could not delete temporary file \"%s\". You can safely delete it.",
                                tmpProjectFile.getAbsolutePath()));
                    }
                    continue;
                }

                Path entry = root == null ? childPath : Paths.get(root.toString(), childPath.toString());
                if (Files.isDirectory(child)) {
                    // Before recursing, we add a ZipEntry for the directory to allow
                    // empty dirs.
                    boolean emptyDir = child.toFile().listFiles().length == 0;
                    if (emptyDir) {
                        createEmptyFile(out, entry);
                    }
                    int added = addZipDir(out, entry, child, props, filter);
                    if (!emptyDir && added == 0) {
                        createEmptyFile(out, entry);
                    }
                    addedFiles += added;
                } else {
                    omtPackLog(String.format("addZipDir\tfile\t[%s]", entry));
                    out.putNextEntry(new ZipEntry(entry.toString().replace("\\", "/")));
                    Files.copy(child, out);
                    addedFiles++;
                    out.closeEntry();
                }
            }
        }
        return addedFiles;
    }

    protected static void omtPackLog(String msg) throws IOException {
        Log.logDebug(LOGGER, msg);
        fhandler.write(msg + "\n");
    }

    private static void createEmptyFile(final ZipOutputStream out, Path entry) throws IOException {
        String emptyDirFile = entry.toString() + File.separatorChar + IGNORE_FILE;
        omtPackLog(String.format("createEmptyFile\t[%s]", emptyDirFile));
        out.putNextEntry(new ZipEntry(emptyDirFile.replace("\\", "/")));
        out.closeEntry();
    }
}
