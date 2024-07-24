/**************************************************************************
 OmegaT - Computer Assisted Translation (CAT) tool
          with fuzzy matching, translation memory, keyword search,
          glossaries, and translation leveraging into updated projects.

 Copyright (C) 2014 Alex Buloichik
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
package org.omegat.core.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import java.io.File;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Properties;

import org.junit.Before;
import org.junit.Test;
import org.omegat.core.Core;
import org.omegat.core.segmentation.SRX;
import org.omegat.core.segmentation.Segmenter;
import org.omegat.tokenizer.LuceneFrenchTokenizer;
import org.omegat.util.TestPreferencesInitializer;

/**
 * Tests for tm/auto/ tmx loading with replace translations.
 *
 * @author Alex Buloichik (alex73mail@gmail.com)
 */
public class AutoTmxTest {
    RealProject p;

    @Before
    public final void setUp() throws Exception {
        TestPreferencesInitializer.init();
        Core.setSegmenter(new Segmenter(SRX.getDefault()));
    }

    @Test
    public void testICE() throws Exception {
        ProjectProperties props = new ProjectProperties();
        props.setSourceLanguage("en");
        props.setTargetLanguage("fr");
        props.setTargetTokenizer(LuceneFrenchTokenizer.class);
        File file = new File("test/data/autotmx/ice/auto1.tmx");
        ExternalTMX autoTMX = new ExternalTMFactory.TMXLoader(file)
                .setDoSegmenting(props.isSentenceSegmentingEnabled())
                .load(props.getSourceLanguage(), props.getTargetLanguage());

        PrepareTMXEntry e1 = autoTMX.getEntries().get(0);
        checkListValues(e1, ProjectTMX.PROP_XICE, "11");

        PrepareTMXEntry e2 = autoTMX.getEntries().get(1);
        checkListValues(e2, ProjectTMX.PROP_XICE, "12");
        checkListValues(e2, ProjectTMX.PROP_X100PC, "10");

        Core.initializeConsole(new HashMap<String, String>());

        p = new RealProject(props);
        p.projectTMX = new ProjectTMX(props.getSourceLanguage(), props.getTargetLanguage(), false, new File(
                "test/data/autotmx/ice/project1.tmx"), new ProjectTMX.CheckOrphanedCallback() {
            public boolean existSourceInProject(String src) {
                return true;
            }

            public boolean existEntryInProject(EntryKey key) {
                return true;
            }
        });
        SourceTextEntry ste10, ste11, ste12;
        p.allProjectEntries.add(ste10 = createSTE("10", "Edit"));
        p.allProjectEntries.add(ste11 = createSTE("11", "Edit"));
        p.allProjectEntries.add(ste12 = createSTE("12", "Edit"));
        p.importHandler = new ImportFromAutoTMX(p, p.allProjectEntries);
        p.appendFromAutoTMX(autoTMX, false);
        checkTranslation(ste10, "Modifier", TMXEntry.ExternalLinked.x100PC);
        checkTranslation(ste11, "Edition", TMXEntry.ExternalLinked.xICE);
        checkTranslation(ste12, "Modifier", TMXEntry.ExternalLinked.xICE);
    }

    @Test
    public void testEnforce1() throws Exception {
        ProjectProperties props = new ProjectProperties();
        props.setSourceLanguage("en");
        props.setTargetLanguage("fr");
        props.setTargetTokenizer(LuceneFrenchTokenizer.class);
        File file = new File("test/data/autotmx/enforce-test1/enforce1.tmx");
        ExternalTMX enforceTMX = new ExternalTMFactory.TMXLoader(file)
                .setDoSegmenting(props.isSentenceSegmentingEnabled())
                .load(props.getSourceLanguage(), props.getTargetLanguage());

        Core.initializeConsole(new HashMap<String, String>());

        p = new RealProject(props);
        p.projectTMX = new ProjectTMX(props.getSourceLanguage(), props.getTargetLanguage(), false,
                new File("test/data/autotmx/enforce-test1/project1.tmx"), new ProjectTMX.CheckOrphanedCallback() {
                    public boolean existSourceInProject(String src) {
                        return true;
                    }

                    public boolean existEntryInProject(EntryKey key) {
                        return true;
                    }
                });

        SourceTextEntry ste;
        p.allProjectEntries.add(ste = createSTE(null, "Edit"));
        checkTranslation(ste, "foobar", null);
        p.importHandler = new ImportFromAutoTMX(p, p.allProjectEntries);
        p.appendFromAutoTMX(enforceTMX, true);
        checkTranslation(ste, "bizbaz", TMXEntry.ExternalLinked.xENFORCED);
    }
    
    SourceTextEntry createSTE(String id, String source) {
        EntryKey ek = new EntryKey("file", source, id, null, null, null);
        return new SourceTextEntry(ek, 0, null, null, new ArrayList<ProtectedPart>());
    }

    void checkListValues(PrepareTMXEntry en, String propType, String propValue) {
        assertTrue(en.hasPropValue(propType, propValue));
    }

    void checkTranslation(SourceTextEntry ste, String expectedTranslation,
            TMXEntry.ExternalLinked expectedExternalLinked) {
        TMXEntry e = p.getTranslationInfo(ste);
        assertTrue(e.isTranslated());
        assertEquals(expectedTranslation, e.translation);
        assertEquals(expectedExternalLinked, e.linked);
    }
    
    @Test
    public void testEnforce2() throws Exception {
        ProjectProperties props = new ProjectProperties();
        props.setSourceLanguage("en");
        props.setTargetLanguage("fr");
        props.setTargetTokenizer(LuceneFrenchTokenizer.class);
        File file = new File("test/data/autotmx/enforce-test2/enforce.tmx");
        ExternalTMX enforceTMX = new ExternalTMFactory.TMXLoader(file)
                .setDoSegmenting(props.isSentenceSegmentingEnabled())
                .load(props.getSourceLanguage(), props.getTargetLanguage());

        Core.initializeConsole(new HashMap<String, String>());

        p = new RealProject(props);
        p.projectTMX = new ProjectTMX(props.getSourceLanguage(), props.getTargetLanguage(), false,
                new File("test/data/autotmx/enforce-test2/project_save.tmx"), new ProjectTMX.CheckOrphanedCallback() {
                    public boolean existSourceInProject(String src) {
                        return true;
                    }

                    public boolean existEntryInProject(EntryKey key) {
                        return true;
                    }
                });
        HashMap<String,SourceTextEntry> entriesMap = new HashMap<>();
        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(
            new java.io.FileInputStream("test/data/autotmx/enforce-test2/source-file.properties")))) {
            String line; SourceTextEntry ste;
            while ((line = reader.readLine()) != null) {
                if (! line.contains("=")) continue;                
                String key = line.substring(0, line.indexOf("=")), val = line.substring(line.indexOf("=") + 1);
                p.allProjectEntries.add(ste = createSTE("source-file.properties", key, val));
                entriesMap.put(key, ste);
            }
        }
        p.importHandler = new ImportFromAutoTMX(p, p.allProjectEntries);
        p.appendFromAutoTMX(enforceTMX, true);
        TMXEntry e = p.getTranslationInfo(entriesMap.get("tu_0_A"));
        assertFalse(e.isTranslated());
        e = p.getTranslationInfo(entriesMap.get("tu_0_B"));
        assertFalse(e.isTranslated());
        checkTranslation(entriesMap.get("tu_1_A"), "Traduction par défaut dans enforce", TMXEntry.ExternalLinked.xENFORCED);
        checkTranslation(entriesMap.get("tu_1_B"), "Traduction par défaut dans enforce", TMXEntry.ExternalLinked.xENFORCED);
        checkTranslation(entriesMap.get("tu_1_C"), "Traduction par défaut dans enforce", TMXEntry.ExternalLinked.xENFORCED);
        checkTranslation(entriesMap.get("tu_2_A"), "Traduction alternative dans enforce (première instance uniquement)", TMXEntry.ExternalLinked.xENFORCED);
        checkTranslation(entriesMap.get("tu_2_B"), "Traduction par défaut dans enforce (pour les autres)", TMXEntry.ExternalLinked.xENFORCED);
        checkTranslation(entriesMap.get("tu_2_C"), "Traduction par défaut dans enforce (pour les autres)", TMXEntry.ExternalLinked.xENFORCED);
        checkTranslation(entriesMap.get("tu_3_A"), "Traduction par défaut dans enforce propagée partout", TMXEntry.ExternalLinked.xENFORCED);
        checkTranslation(entriesMap.get("tu_3_B"), "Traduction par défaut dans enforce propagée partout", TMXEntry.ExternalLinked.xENFORCED);
        checkTranslation(entriesMap.get("tu_3_C"), "Traduction par défaut dans enforce propagée partout", TMXEntry.ExternalLinked.xENFORCED);
        checkTranslation(entriesMap.get("tu_4_A"), "Traduction écrasante", TMXEntry.ExternalLinked.xENFORCED);
        checkTranslation(entriesMap.get("tu_4_B"), "Traduction par défaut dans la mémoire (écrasée une fois)", null);
        checkTranslation(entriesMap.get("tu_4_C"), "Traduction par défaut dans la mémoire (écrasée une fois)", null);
        checkTranslation(entriesMap.get("tu_5_A"), "Traduction par défaut dans enforce", TMXEntry.ExternalLinked.xENFORCED);
        checkTranslation(entriesMap.get("tu_5_B"), "Traduction alternative dans la mémoire", null); // not erased by enforce default
        checkTranslation(entriesMap.get("tu_5_C"), "Traduction par défaut dans enforce", TMXEntry.ExternalLinked.xENFORCED);
        checkTranslation(entriesMap.get("tu_6_A"), "Traduction par défaut dans la mémoire", null);
        checkTranslation(entriesMap.get("tu_6_B"), "Traduction alternative dans enforce", TMXEntry.ExternalLinked.xENFORCED); // not erased by enforce default
        checkTranslation(entriesMap.get("tu_6_C"), "Traduction par défaut dans la mémoire", null);
    }
    
    SourceTextEntry createSTE(String file, String id, String source) {
        EntryKey ek = new EntryKey(file, source, id, null, null, null);
        return new SourceTextEntry(ek, 0, null, null, new ArrayList<ProtectedPart>());
    }    
}
