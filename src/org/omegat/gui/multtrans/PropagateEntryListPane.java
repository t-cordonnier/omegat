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

import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JFrame;
import javax.swing.JTextPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.Document;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

import org.omegat.core.Core;
import org.omegat.core.data.SourceTextEntry;
import org.omegat.gui.editor.IEditor;
import org.omegat.gui.editor.IEditor.CaretPosition;
import org.omegat.gui.editor.IEditorFilter;
import org.omegat.gui.shortcuts.PropertiesShortcuts;
import org.omegat.util.Java8Compat;
import org.omegat.util.Log;
import org.omegat.util.OStrings;
import org.omegat.util.Preferences;
import org.omegat.util.StringUtil;
import org.omegat.util.gui.FontFallbackListener;
import org.omegat.util.gui.StaticUIUtils;
import org.omegat.util.gui.Styles;
import org.omegat.util.gui.UIThreadsUtil;

/**
 * PropagateEntryListPane displays translation segments and, upon doubleclick of a
 * segment, instructs the main UI to jump to that segment this replaces the
 * previous huperlink interface and is much more flexible in the fonts it
 * displays than the HTML text
 *
 * @author Thomas Cordonnier (based on Search entry list pane)
 */
@SuppressWarnings("serial")
class PropagateEntryListPane extends JTextPane {
    protected static final int MARKS_PER_REQUEST = 100;
    protected static final String ENTRY_SEPARATOR = "---------\n";
    private static final String KEY_GO_TO_NEXT_SEGMENT = "gotoNextSegmentMenuItem";
    private static final String KEY_GO_TO_PREVIOUS_SEGMENT = "gotoPreviousSegmentMenuItem";
    private static final String KEY_TRANSFER_FOCUS = "transferFocus";
    private static final String KEY_TRANSFER_FOCUS_BACKWARD = "transferFocusBackward";
    private static final String KEY_JUMP_TO_ENTRY_IN_EDITOR = "jumpToEntryInEditor";
    private static final int ENTRY_LIST_INDEX_NO_ENTRIES  = -1;
    private static final int ENTRY_LIST_INDEX_END_OF_TEXT = -2;

    private static void bindKeyStrokesFromMainMenuShortcuts(InputMap map) {
        // Add KeyStrokes Ctrl+N/P (Cmd+N/P for MacOS) to the map
        PropertiesShortcuts.getMainMenuShortcuts().bindKeyStrokes(map,
                KEY_GO_TO_NEXT_SEGMENT, KEY_GO_TO_PREVIOUS_SEGMENT, KEY_JUMP_TO_ENTRY_IN_EDITOR);
    }

    private static InputMap createDefaultInputMap(InputMap parent) {
        InputMap map = new InputMap();
        map.setParent(parent);
        bindKeyStrokesFromMainMenuShortcuts(map);

        // Add KeyStrokes: Enter, Ctrl+Enter (Cmd+Enter for MacOS)
        map.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), KEY_GO_TO_NEXT_SEGMENT);
        map.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER,
                Java8Compat.getMenuShortcutKeyMaskEx()), KEY_GO_TO_PREVIOUS_SEGMENT);
        return map;
    }

    private static InputMap createDefaultInputMapUseTab(InputMap parent) {
        InputMap map = new InputMap();
        map.setParent(parent);
        bindKeyStrokesFromMainMenuShortcuts(map);

        // Add KeyStrokes: Tab, Shift+Tab, Ctrl+Tab, Ctrl+Shift+Tab
        // (Cmd+Tab is used by the system on OS X)
        map.put(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0),
                KEY_GO_TO_NEXT_SEGMENT);
        map.put(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.SHIFT_DOWN_MASK),
                KEY_GO_TO_PREVIOUS_SEGMENT);
        map.put(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.CTRL_DOWN_MASK),
                KEY_TRANSFER_FOCUS);
        map.put(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK),
                KEY_TRANSFER_FOCUS_BACKWARD);
        // Enter to jump to selected segment in editor
        map.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), KEY_JUMP_TO_ENTRY_IN_EDITOR);
        return map;
    }

    PropagateEntryListPane() {
        setDocument(new DefaultStyledDocument());

        setDragEnabled(true);
        setFont(Core.getMainWindow().getApplicationFont());
        StaticUIUtils.makeCaretAlwaysVisible(this);
        StaticUIUtils.setCaretUpdateEnabled(this, false);

        setForeground(Styles.EditorColor.COLOR_FOREGROUND.getColor());
        setCaretColor(Styles.EditorColor.COLOR_FOREGROUND.getColor());
        setBackground(Styles.EditorColor.COLOR_BACKGROUND.getColor());

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    if (!autoSyncWithEditor && !entryList.isEmpty()) {
                        getActiveDisplayedEntry().gotoEntryInEditor();
                        JFrame frame = Core.getMainWindow().getApplicationFrame();
                        frame.setState(JFrame.NORMAL);
                        frame.toFront();
                    }
                }
            }
        });

        addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                boolean useTabForAdvance = Core.getEditor().getSettings().isUseTabForAdvance();
                if (PropagateEntryListPane.this.useTabForAdvance != useTabForAdvance) {
                    PropagateEntryListPane.this.useTabForAdvance = useTabForAdvance;
                    initInputMap(useTabForAdvance);
                }
            }
        });

        addCaretListener(e -> {
            SwingUtilities.invokeLater(highlighter);

            if (autoSyncWithEditor) {
                getActiveDisplayedEntry().gotoEntryInEditor();
            }
        });

        setDocument(new DefaultStyledDocument());
        getDocument().addDocumentListener(new FontFallbackListener(PropagateEntryListPane.this));

        initActions();
        useTabForAdvance = Core.getEditor().getSettings().isUseTabForAdvance();
        autoSyncWithEditor = Preferences.isPreferenceDefault(Preferences.SEARCHWINDOW_AUTO_SYNC, false);
        initInputMap(useTabForAdvance);
        setEditable(false);
    }

    private void initInputMap(boolean useTabForAdvance) {
        setFocusTraversalKeysEnabled(!useTabForAdvance);
        InputMap parent = getInputMap().getParent();
        InputMap newMap = useTabForAdvance ? createDefaultInputMapUseTab(parent)
                                           : createDefaultInputMap(parent);
        setInputMap(WHEN_FOCUSED, newMap);
    }

    void setAutoSyncWithEditor(boolean autoSyncWithEditor) {
        this.autoSyncWithEditor = autoSyncWithEditor;
        if (autoSyncWithEditor) {
            getActiveDisplayedEntry().gotoEntryInEditor();
        }
    }

    /**
     * Show search result for user
     */
    public void displaySearchResult(List<SourceTextEntry> entries, int numberOfResults) {
        UIThreadsUtil.mustBeSwingThread();

        this.numberOfResults = numberOfResults;

        currentlyDisplayedMatches = null;
        entryList.clear();
        offsetList.clear();
        firstMatchList.clear();

        if ((entries == null) || (entries.isEmpty())) {
            // empty marks - just reset
            setText("");
            return;
        }

        currentlyDisplayedMatches = new DisplayMatches(entries);

        highlighter.reset();
        SwingUtilities.invokeLater(highlighter);

        if (autoSyncWithEditor) {
            getActiveDisplayedEntry().gotoEntryInEditor();
        }
    }

    private int getActiveEntryListIndex() {
        int nrEntries = getNrEntries();

        if (nrEntries == 0) {
            // No entry
            return ENTRY_LIST_INDEX_NO_ENTRIES;
        }

        if (nrEntries > 0) {
            int pos = getSelectionStart();
            for (int i = 0; i < nrEntries; i++) {
                if (pos < offsetList.get(i)) {
                    return i;
                }
            }
        }

        return ENTRY_LIST_INDEX_END_OF_TEXT;
    }

    protected class DisplayMatches {
        private final List<SourceTextEntry> matches = new ArrayList<SourceTextEntry>();

        public DisplayMatches(final List<SourceTextEntry> entries) {
            UIThreadsUtil.mustBeSwingThread();

            StringBuilder stringBuf = new StringBuilder();
            // display what's been found so far
            if (entries.isEmpty()) {
                // no match
                addMessage(stringBuf, OStrings.getString("ST_NOTHING_FOUND"));
            }

            if (entries.size() >= numberOfResults) {
                addMessage(stringBuf, StringUtil.format(OStrings.getString("SW_MAX_FINDS_REACHED"),
                        numberOfResults));
            }

            for (SourceTextEntry e : entries) {
                addEntry(stringBuf, e);
            }

            Document doc = getDocument();
            try {
                doc.remove(0, doc.getLength());
                doc.insertString(0, stringBuf.toString(), null);
            } catch (Exception ex) {
                Log.log(ex);
            }
        }

        // add entry text - remember what its number is and where it ends
        public void addEntry(StringBuilder stringBuf, SourceTextEntry ste) {
            if (stringBuf.length() > 0) {
                stringBuf.append(ENTRY_SEPARATOR);
            }
            stringBuf.append(ste.entryNum()).append("\n");
            stringBuf.append(ste.getKey().file).append("\n");
            if (ste.getKey().id != null) {
                stringBuf.append(OStrings.getString("SEGPROP_KEY_ID")).append(": ").append(ste.getKey().id).append("\n");            
            }
            if (ste.getKey().path != null) {
                stringBuf.append(OStrings.getString("SEGPROP_KEY_PATH")).append(": ").append(ste.getKey().path).append("\n");            
            }
            if (ste.getKey().prev != null) {
                stringBuf.append(OStrings.getString("SEGPROP_KEY_PREV")).append(": ").append(ste.getKey().prev).append("\n");            
            }
            if (ste.getKey().next != null) {
                stringBuf.append(OStrings.getString("SEGPROP_KEY_NEXT")).append(": ").append(ste.getKey().next).append("\n");            
            }
            
            entryList.add(ste.entryNum());
            offsetList.add(stringBuf.length());
        }
    }

    /**
     * Adds a message text to be displayed. Used for displaying messages that
     * aren't results.
     *
     * @param message
     *            The message to display
     */
    private void addMessage(StringBuilder stringBuf, String message) {
        // Insert entry/message separator if necessary
        if (stringBuf.length() > 0) {
            stringBuf.append(ENTRY_SEPARATOR);
        }
        // Insert the message text
        stringBuf.append(message);
    }

    public void reset() {
        displaySearchResult(null, 0);
    }

    public int getNrEntries() {
        return entryList.size();
    }

    public List<Integer> getEntryList() {
        return entryList;
    }

    private void initActions() {
        ActionMap actionMap = getActionMap();

        // go to next segment
        actionMap.put(KEY_GO_TO_NEXT_SEGMENT, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent arg0) {
                getActiveDisplayedEntry().getNext().activate();
            }
        });

        // go to previous segment
        actionMap.put(KEY_GO_TO_PREVIOUS_SEGMENT, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent arg0) {
                getActiveDisplayedEntry().getPrevious().activate();
            }
        });

        // transfer focus to next component
        actionMap.put(KEY_TRANSFER_FOCUS, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent arg0) {
                transferFocus();
            }
        });

        // transfer focus to previous component
        actionMap.put(KEY_TRANSFER_FOCUS_BACKWARD, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent arg0) {
                transferFocusBackward();
            }
        });

        actionMap.put(KEY_JUMP_TO_ENTRY_IN_EDITOR, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!autoSyncWithEditor && !entryList.isEmpty()) {
                    getActiveDisplayedEntry().gotoEntryInEditor();
                }
            }
        });
    }

    private DisplayedEntry getActiveDisplayedEntry() {
        int activeEntryListIndex = getActiveEntryListIndex();

        switch (activeEntryListIndex) {
            case ENTRY_LIST_INDEX_NO_ENTRIES:
                return new EmptyDisplayedEntry();
            case ENTRY_LIST_INDEX_END_OF_TEXT:
                // end of text (out of entries range)
                return new DisplayedEntryImpl(getNrEntries());
            default:
                return new DisplayedEntryImpl(activeEntryListIndex);
        }
    }

    private interface DisplayedEntry {

        DisplayedEntry getNext();

        DisplayedEntry getPrevious();

        void activate();

        void gotoEntryInEditor();
    }

    private static class EmptyDisplayedEntry implements DisplayedEntry {

        @Override
        public DisplayedEntry getNext() {
            return this;
        }

        @Override
        public DisplayedEntry getPrevious() {
            return this;
        }

        @Override
        public void activate() {
            // Do nothing
        }

        @Override
        public void gotoEntryInEditor() {
            // Do nothing
        }

    }

    private class DisplayedEntryImpl implements DisplayedEntry {

        private final int index;

        DisplayedEntryImpl(int index) {
            this.index = index;
        }

        @Override
        public DisplayedEntry getNext() {
            if (index >= (getNrEntries() - 1)) {
                return this;
            } else {
                return new DisplayedEntryImpl(index + 1);
            }
        }

        @Override
        public DisplayedEntry getPrevious() {
            if (index == 0) {
                return this;
            } else {
                return new DisplayedEntryImpl(index - 1);
            }
        }

        @Override
        public void activate() {
            if (index >= getNrEntries()) {
                // end of text (out of entries range)
                return;
            }

            int beginPos = 0;
            if (index != 0) {
                beginPos = offsetList.get(index - 1) + ENTRY_SEPARATOR.length();
                int endPos = offsetList.get(index);
                try {
                    Rectangle endRect = Java8Compat.modelToView(PropagateEntryListPane.this, endPos);
                    scrollRectToVisible(endRect);
                } catch (BadLocationException ex) {
                    // Eat exception silently
                }
            }
            setSelectionStart(beginPos);
            setSelectionEnd(beginPos);
        }

        @Override
        public void gotoEntryInEditor() {
            if (index >= getNrEntries()) {
                // end of text (out of entries range)
                return;
            }

            final int entry = entryList.get(index);
            if (entry > 0) {
                final IEditor editor = Core.getEditor();
                int currEntryInEditor = editor.getCurrentEntryNumber();
                if (currEntryInEditor != 0 && entry != currEntryInEditor) {
                    final boolean isSegDisplayed = isSegmentDisplayed(entry);
                    SwingUtilities.invokeLater(() -> {
                        if (isSegDisplayed && firstMatchList.containsKey(entry)) {
                            // Select search word in Editor pane
                            CaretPosition pos = firstMatchList.get(entry);
                            editor.gotoEntry(entry, pos);
                        } else {
                            editor.gotoEntry(entry);
                        }
                    });
                }
            }
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
    }

    private class SegmentHighlighter implements Runnable {

        private final AttributeSet attrNormal;
        private final AttributeSet attrActive;

        private int entryListIndex = -1;
        private int offset         = -1;
        private int length         = -1;

        SegmentHighlighter() {
            MutableAttributeSet attrNormal = new SimpleAttributeSet();
            StyleConstants.setBackground(attrNormal, Styles.EditorColor.COLOR_BACKGROUND.getColor());
            this.attrNormal = attrNormal;

            MutableAttributeSet attrActive = new SimpleAttributeSet();
            // This is the same as the default value for
            // Styles.EditorColor.COLOR_ACTIVE_SOURCE, but we hard-code it here
            // because this panel does not currently support customized colors.
            StyleConstants.setBackground(attrActive, Styles.EditorColor.COLOR_ACTIVE_SOURCE.getColor());
            this.attrActive = attrActive;
        }

        @Override
        public void run() {
            int activeEntryListIndex = getActiveEntryListIndex();
            if (activeEntryListIndex == ENTRY_LIST_INDEX_END_OF_TEXT) {
                // end of text (out of entries range) should belongs to the last segment
                activeEntryListIndex = getNrEntries() - 1;
            }

            if (activeEntryListIndex != entryListIndex) {
                removeCurrentHighlight();
                addHighlight(activeEntryListIndex);
            }
        }

        public void reset() {
            entryListIndex = -1;
            offset = -1;
            length = -1;
        }

        private void removeCurrentHighlight() {
            if (entryListIndex == -1 || entryListIndex >= offsetList.size() || length <= 0) {
                return;
            }

            getStyledDocument().setCharacterAttributes(offset, length, attrNormal, false);
            reset();
        }

        private void addHighlight(int entryListIndex) {
            if (entryListIndex == -1 || entryListIndex >= offsetList.size()) {
                return;
            }

            int offset = entryListIndex == 0 ? 0
                    : offsetList.get(entryListIndex - 1) + ENTRY_SEPARATOR.length();
            int length = offsetList.get(entryListIndex) - offset - 1; // except tail line break

            getStyledDocument().setCharacterAttributes(offset, length, attrActive, false);

            this.entryListIndex = entryListIndex;
            this.offset = offset;
            this.length = length;
        }
    }

    private final List<Integer> entryList = new ArrayList<>();
    private final List<Integer> offsetList = new ArrayList<>();
    private final Map<Integer, CaretPosition> firstMatchList = new HashMap<>();
    private DisplayMatches currentlyDisplayedMatches;
    private int numberOfResults;
    private boolean useTabForAdvance;
    private boolean autoSyncWithEditor;
    private final SegmentHighlighter highlighter = new SegmentHighlighter();
}
