/**************************************************************************
 OmegaT - Computer Assisted Translation (CAT) tool
          with fuzzy matching, translation memory, keyword search,
          glossaries, and translation leveraging into updated projects.

 Copyright (C) 2000-2006 Keith Godfrey and Maxym Mykhalchuk
               2009 Alex Buloichik
               2012 Thomas Cordonnier
               2013-2014 Aaron Madlon-Kay
               2024 Thomas Cordonnier
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

package org.omegat.core.matching;

import java.util.Iterator;
import java.util.Comparator;
import java.util.List;

import org.omegat.core.data.EntryKey;
import org.omegat.util.Preferences;
import org.omegat.util.StringUtil;
import org.omegat.util.TMXProp;

/**
 * Class to hold a single fuzzy match.
 *
 * @author Keith Godfrey
 * @author Maxym Mykhalchuk
 * @author Thomas Cordonnier
 * @author Aaron Madlon-Kay
 */
public class NearString {
    public enum MATCH_SOURCE {
        MEMORY, TM, FILES
    };

    public enum SORT_KEY {
        SCORE, SCORE_NO_STEM, ADJUSTED_SCORE
    }

    public NearString(final EntryKey key, final String source, final String translation, MATCH_SOURCE comesFrom,
            final boolean fuzzyMark, final int nearScore, final int nearScoreNoStem, final int adjustedScore,
            final byte[] nearData, final String projName, final String creator, final long creationDate,
            final String changer, final long changedDate, final List<TMXProp> props) {
        this.key = key;
        this.source = source;
        this.translation = translation;
        this.comesFrom = comesFrom;
        this.fuzzyMark = fuzzyMark;
        this.score = nearScore; this.scoreNoStem = nearScoreNoStem; this.adjustedScore = adjustedScore;
        this.attr = nearData;
        this.proj = projName;
        this.props = props;
        this.creator = creator;
        this.creationDate = creationDate;
        this.changer = changer;
        this.changedDate = changedDate;
    }

    // Merge : build a single-chained list (only for merge, not for list!)
    private NearString nextMerged = null; // will be filled during merging only

    public static NearString merge(NearString ns, final EntryKey key, final String source, final String translation,
            MATCH_SOURCE comesFrom, final boolean fuzzyMark, final int nearScore, final int nearScoreNoStem,
            final int adjustedScore, final byte[] nearData, final String projName, final String creator,
            final long creationDate, final String changer, final long changedDate, final List<TMXProp> props) {

        NearString merged = new NearString(key, source, translation, comesFrom, fuzzyMark, nearScore, nearScoreNoStem,
                adjustedScore, nearData, projName, creator, creationDate, changer, changedDate, props);
        return merge(ns, merged);
    }

    private static NearString merge(NearString ns, NearString merged) {
        ScoresComparator comparator = new ScoresComparator();
        int cmp = comparator.compare(merged, ns); boolean first = cmp > 0;
        if (cmp == 0) {
            if (merged.proj == null) {
                if (ns.proj != null) {
                    first = true;
                }
            } else {
                if (ns.proj == null) {
                    first = false;
                } else {
                    first = merged.proj.compareTo(ns.proj) < 0;
                }
            }
        }

        if (first) { merged.nextMerged = ns; return merged; } // returns merged followed by ns
        else if (ns.nextMerged == null) { ns.nextMerged = merged; return ns; }  // return ns followed my merge
        else { ns.nextMerged = NearString.merge(ns.nextMerged, merged); return ns; }	// inserts merged inside ns.nextMerged
    }
    
    public Iterator<NearString> getMergedEntries() {
        return new Iterator<NearString>() {
            private NearString current = NearString.this;
            
            public boolean hasNext() { return current.nextMerged != null; }
            
            public NearString next() { return current = current.nextMerged; }
            
        };
    }
    
    public int mergedCount() {
        int count = 1;
        for (Iterator<NearString> iter = getMergedEntries(); iter.hasNext(); ) {
            count = count + 1;
            iter.next();
        }
        return count;
    }
    
    public boolean isMerged() {
        return nextMerged != null;
    }

    @Override
    public String toString() {
        return String.join(" ", StringUtil.truncate(source, 20), scoresToString(), "x" + mergedCount());
    }

    public EntryKey key;
    public String source;
    public String translation;
    public MATCH_SOURCE comesFrom;

    public boolean fuzzyMark;

    public final int score;
    /** similarity score for match without non-word tokens */
    public final int scoreNoStem;
    /** adjusted similarity score for match including all tokens */
    public final int adjustedScore;

    /** matching attributes of near strEntry */
    public byte[] attr;
    public String proj;
    public List<TMXProp> props;
    public String creator;
    public long creationDate;
    public String changer;
    public long changedDate;

    public String scoresToString() {
        StringBuilder b = new StringBuilder();
        b.append("(");
        b.append(score);
        b.append("/");
        b.append(scoreNoStem);
        b.append("/");
        b.append(adjustedScore);
        b.append("%)");
        return b.toString();
    }

    public static class ScoresComparator implements Comparator<NearString> {

        private final SORT_KEY key;

        public ScoresComparator() {
            this.key = Preferences.getPreferenceEnumDefault(Preferences.EXT_TMX_SORT_KEY, SORT_KEY.SCORE);
        }

        public ScoresComparator(SORT_KEY key) {
            this.key = key;
        }

        @Override
        public int compare(NearString o1, NearString o2) {
            int s1 = primaryScore(o1);
            int s2 = primaryScore(o2);
            if (s1 != s2) {
                return s1 > s2 ? 1 : -1;
            }
            s1 = secondaryScore(o1);
            s2 = secondaryScore(o2);
            if (s1 != s2) {
                return s1 > s2 ? 1 : -1;
            }
            s1 = ternaryScore(o1);
            s2 = ternaryScore(o2);
            if (s1 != s2) {
                return s1 > s2 ? 1 : -1;
            }
            return 0;
        }

        private int primaryScore(NearString s) {
            switch (key) {
            case SCORE:
                return s.score;
            case SCORE_NO_STEM:
                return s.scoreNoStem;
            case ADJUSTED_SCORE:
            default:
                return s.adjustedScore;
            }
        }

        private int secondaryScore(NearString s) {
            switch (key) {
            case SCORE:
                return s.scoreNoStem;
            case SCORE_NO_STEM:
                return s.score;
            case ADJUSTED_SCORE:
            default:
                return s.score;
            }
        }

        private int ternaryScore(NearString s) {
            switch (key) {
            case SCORE:
                return s.adjustedScore;
            case SCORE_NO_STEM:
                return s.adjustedScore;
            case ADJUSTED_SCORE:
            default:
                return s.scoreNoStem;
            }
        }
    }
}
