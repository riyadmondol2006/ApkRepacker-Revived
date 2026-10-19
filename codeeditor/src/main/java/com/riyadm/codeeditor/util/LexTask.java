package com.riyadm.codeeditor.util;

import com.riyadm.codeeditor.lang.Language;

import java.util.List;

public abstract class LexTask {
    protected final Language language;
    /**
     * Set by another thread to stop the scan immediately; tokenize() loops
     * check it on every token
     */
    protected volatile boolean abort;

    public LexTask(Language language) {
        this.language = language;
        abort = false;
    }

    public void abort() {
        abort = true;
    }

    public int format(IndentStringBuilder sb, CharSequence input, int width, int curPos) {
        sb.append(input);
        return curPos;
    }

    public boolean canFormat() {
        return false;
    }

    public Selection expandSelection(String text, int oldStart, int oldEnd) {
        return new Selection(0, text.length() - 1);
    }

    /**
     * Fills _tokens with Pairs, where Pair.first is the end position
     * (inclusive) of the token, in increasing order, and Pair.second is the
     * type of the token. Runs on the lexer thread and should return early
     * once abort is set.
     */
    protected abstract void tokenize(List<Pair> _tokens, String text);

    public final Language getLanguage() {
        return language;
    }

    public abstract String getLanguageType();


    public static class Selection {
        public final int start, len;

        public Selection(int start, int end) {
            this.start = start;
            this.len = end - start;
        }
    }
}
