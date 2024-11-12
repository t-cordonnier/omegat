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

package org.omegat.gui.multtrans;

import java.awt.Component;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.File;
import java.text.MessageFormat;
import java.text.ParseException;
import java.util.Iterator;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.DefaultComboBoxModel;
import javax.swing.InputMap;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SpinnerDateModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.undo.UndoManager;

import org.omegat.core.Core;
import org.omegat.core.data.TMXEntry;
import org.omegat.core.data.PrepareTMXEntry;
import org.omegat.core.data.SourceTextEntry;
import org.omegat.core.search.SearchExpression;
import org.omegat.core.search.SearchMode;
import org.omegat.core.search.Searcher;
import org.omegat.core.threads.SearchThread;
import org.omegat.gui.editor.EditorController;
import org.omegat.gui.editor.IEditor;
import org.omegat.gui.editor.IEditor.CaretPosition;
import org.omegat.gui.editor.IEditorFilter;
import org.omegat.gui.editor.filter.ReplaceFilter;
import org.omegat.gui.editor.filter.SearchFilter;
import org.omegat.util.Java8Compat;
import org.omegat.util.Log;
import org.omegat.util.OConsts;
import org.omegat.util.OStrings;
import org.omegat.util.Platform;
import org.omegat.util.Preferences;
import org.omegat.util.StringUtil;
import org.omegat.util.gui.OSXIntegration;
import org.omegat.util.gui.OmegaTFileChooser;
import org.omegat.util.gui.StaticUIUtils;
import org.omegat.util.gui.UIThreadsUtil;
import org.openide.awt.Mnemonics;

/**
 * This is a window that appears when user'd like to search for something. For
 * each new user's request new window is created. Actual search is done by
 * SearchThread.
 *
 * @author Thomas Cordonnier
 */
@SuppressWarnings("serial")
public class PropagateSearchWindowController {

    private final PropagateSearchWindowForm form;
    private final int initialEntry;
    private final CaretPosition initialCaret;

    public PropagateSearchWindowController() {
        form = new PropagateSearchWindowForm();
        initialEntry = Core.getEditor().getCurrentEntryNumber();
        initialCaret = getCurrentPositionInEntryTranslationInEditor(Core.getEditor());

        if (Platform.isMacOSX()) {
            OSXIntegration.enableFullScreen(form);
        }

        loadPreferences();
        initActions();
        doSearch();
    }


    final void initActions() {

        // ///////////////////////////////////
        // action listeners
        form.m_dismissButton.addActionListener(e -> doCancel());
        form.m_filterButton.addActionListener(e -> doFilter());
        form.m_propagateAllButton.addActionListener(e -> doPropagateAll());

        form.m_searchButton.addActionListener(e -> doSearch());

        StaticUIUtils.setEscapeAction(form, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                doCancel();
            }
        });

        form.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                // save user preferences
                savePreferences();

                // back to the initial segment
                int currentEntry = Core.getEditor().getCurrentEntryNumber();
                if (initialEntry > 0 && form.m_backToInitialSegment.isSelected() && initialEntry != currentEntry) {
                    boolean isSegDisplayed = isSegmentDisplayed(initialEntry);
                    if (isSegDisplayed) {
                        // Restore caretPosition too
                        ((EditorController) Core.getEditor()).gotoEntry(initialEntry, initialCaret);
                    } else {
                        // The segment is not displayed (maybe filter on). Ignore caretPosition.
                        Core.getEditor().gotoEntry(initialEntry);
                    }
                }
            }
        });
    }


    /**
     * Loads the position and size of the search window and the button selection
     * state.
     */
    private void loadPreferences() {
        // set default size and position
        form.setSize(800, 700);
        StaticUIUtils.persistGeometry(form, Preferences.SEARCHWINDOW_GEOMETRY_PREFIX);

        form.cb_OnlyUntranslated.setSelected(Preferences.isPreference(Preferences.PROPAGATION_SELECT_TRA_ONLY));
        String direction = Preferences.getPreferenceDefault(Preferences.PROPAGATION_SELECT_DIR, "both");
        form.rbBoth.setSelected("both".equals(direction));
        form.rbBack.setSelected("back".equals(direction));
        form.rbFront.setSelected("front".equals(direction));    
    }

    /**
     * Saves the size and position of the search window and the button selection
     * state
     */
    private void savePreferences() {
        Preferences.setPreference(Preferences.PROPAGATION_SELECT_TRA_ONLY, form.cb_OnlyUntranslated.isSelected());
        if (form.rbBoth.isSelected()) {
            Preferences.setPreference(Preferences.PROPAGATION_SELECT_DIR,"both");
        } else if (form.rbBack.isSelected()) {
            Preferences.setPreference(Preferences.PROPAGATION_SELECT_DIR,"back");
        } else if (form.rbFront.isSelected()) {
            Preferences.setPreference(Preferences.PROPAGATION_SELECT_DIR,"front");
        }

        // need to explicitly save preferences
        // because project might not be open
        Preferences.save();
    }

    // /////////////////////////////////////////////////////////////
    // internal functions

    private void doFilter() {
        PropagateEntryListPane viewer = (PropagateEntryListPane) form.m_viewer;
        Core.getEditor().commitAndLeave(); // Otherwise, the current segment being edited is lost
        Core.getEditor().setFilter(new SearchFilter(viewer.getEntryList()));
    }

    private void doSearch() {
        UIThreadsUtil.mustBeSwingThread();
        
        final SourceTextEntry ste = Core.getEditor().getCurrentEntry();
        final TMXEntry curTra = Core.getProject().getTranslationInfo(ste);
        List<SourceTextEntry> dups = ste.getDuplicates();
        if (dups == null) {
            return;
        }
        PrepareTMXEntry p = new PrepareTMXEntry(curTra);
        dups = new java.util.ArrayList<SourceTextEntry>(dups); // 1-depth copy
        for (Iterator<SourceTextEntry> I = dups.iterator(); I.hasNext(); ) {
            SourceTextEntry dup = I.next();
            if (form.rbBack.isSelected()) {
                if (dup.entryNum() > ste.entryNum()) {
                    I.remove();
                }
            }
            if (form.rbFront.isSelected()) {
                if (dup.entryNum() < ste.entryNum()) {
                    I.remove();
                }
            }
            if (form.cb_OnlyUntranslated.isSelected()) {
                TMXEntry tmx2 = Core.getProject().getTranslationInfo(dup);
                if (tmx2.isTranslated() && (! tmx2.defaultTranslation)) {
                    I.remove();
                }
            }
        }
        
        
        PropagateEntryListPane viewer = (PropagateEntryListPane) form.m_viewer;
        form.m_resultsLabel.setText(StringUtil.format(OStrings.getString("SW_NR_OF_RESULTS"), dups.size()));
        viewer.displaySearchResult(dups, 100);
    }
    
    private void doPropagateAll() {
        final SourceTextEntry ste = Core.getEditor().getCurrentEntry();
        final TMXEntry curTra = Core.getProject().getTranslationInfo(ste);
        List<SourceTextEntry> dups = ste.getDuplicates();
        if (dups == null) {
            return;
        }
        PrepareTMXEntry p = new PrepareTMXEntry(curTra);
        int i = 0;
        for (SourceTextEntry dup: dups) {
            if (form.rbBack.isSelected()) {
                if (dup.entryNum() > ste.entryNum()) {
                    continue;
                }
            }
            if (form.rbFront.isSelected()) {
                if (dup.entryNum() < ste.entryNum()) {
                    continue;
                }
            }
            if (form.cb_OnlyUntranslated.isSelected()) {
                TMXEntry tmx2 = Core.getProject().getTranslationInfo(dup);
                if (tmx2.isTranslated() && (! tmx2.defaultTranslation)) {
                    continue;
                }
            }
            Core.getProject().setTranslation(dup, p, false, curTra.linked);
            i = i + 1;
        }
        form.dispose();
        javax.swing.JOptionPane.showMessageDialog(null, 
            OStrings.getString("MULT_MENU_PROPAGATE_RES").replace("{0}", Integer.toString(i)), 
            OStrings.getString("MULT_MENU_PROPAGATE"), javax.swing.JOptionPane.INFORMATION_MESSAGE);

    }    

    void doCancel() {
        UIThreadsUtil.mustBeSwingThread();
        form.dispose();
    }

    public void dispose() {
        form.dispose();
    }
    
    public void setVisible() {
        form.setVisible(true);
    }

    private boolean isSegmentDisplayed(int entry) {
        IEditorFilter filter = Core.getEditor().getFilter();
        if (filter == null) {
            return true;
        } else {
            SourceTextEntry ste = Core.getProject().getAllEntries().get(entry - 1);
            return filter.allowed(ste);
        }
    }

    /**
     * Get the search window frame
     *
     * @return search window frame
     */
    public JFrame getWindow() {
        return form;
    }

    /*
     * TODO: This should be a method on EditorController exposed via IEditor, NOT here.
     */
    private CaretPosition getCurrentPositionInEntryTranslationInEditor(IEditor editor) {
        if (editor instanceof EditorController) {
            EditorController c = (EditorController) editor;
            int selectionEnd = c.getCurrentPositionInEntryTranslation();
            String selection = c.getSelectedText();
            String translation = c.getCurrentTranslation();

            if (StringUtil.isEmpty(translation) || StringUtil.isEmpty(selection)) {
                // no translation or no selection
                return new CaretPosition(selectionEnd);
            } else {
                // get selected range
                int selectionStart = selectionEnd;
                int pos = 0;
                do {
                    pos = translation.indexOf(selection, pos);
                    if (pos == selectionEnd) {
                        selectionStart = pos;
                        selectionEnd = pos + selection.length();
                        break;
                    } else if ((pos + selection.length()) == selectionEnd) {
                        selectionStart = pos;
                        break;
                    }
                    pos++;
                } while (pos > 0);
                return new CaretPosition(selectionStart, selectionEnd);
            }
        } else {
            return CaretPosition.startOfEntry();
        }
    }

    /**
     * Display message dialog with the error as message
     *
     * @param ex
     *            exception to show
     * @param errorKey
     *            error message key in resource bundle
     * @param params
     *            error text parameters
     */
    public void displayErrorRB(final Throwable ex, final String errorKey, final Object... params) {
        UIThreadsUtil.executeInSwingThread(() -> {
            String msg;
            if (params != null) {
                msg = StringUtil.format(OStrings.getString(errorKey), params);
            } else {
                msg = OStrings.getString(errorKey);
            }

            String fulltext = msg;
            if (ex != null) {
                fulltext += "\n" + ex.getLocalizedMessage();
            }
            JOptionPane.showMessageDialog(form, fulltext, OStrings.getString("TF_ERROR"),
                    JOptionPane.ERROR_MESSAGE);
        });
    }


}
