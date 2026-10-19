package com.riyadm.codeeditor.util;

import java.util.Arrays;
import java.util.List;

/**
 * The edits made to a text since its spans were lexed. Relexing waits for
 * typing to pause, so until the new spans arrive the old span ends are
 * moved along with the text instead of coloring it one char off.
 * <p>
 * Instances are immutable; the buffer swaps in a new one on every edit, so
 * a draw on another thread always sees a consistent list.
 */
public final class SpanShift {
	/** Edits beyond this are not tracked and the spans are drawn unshifted */
	static final int MAX_EDITS = 64;

	/** Spans fresh from the lexer */
	public static final SpanShift NONE = new SpanShift(new int[0], true);
	/** Too many edits, or the whole text was replaced */
	static final SpanShift UNTRACKED = new SpanShift(new int[0], false);

	/** Pairs of (offset, delta): delta > 0 chars inserted, delta < 0 chars deleted */
	private final int[] _edits;
	private final boolean _tracking;

	private SpanShift(int[] edits, boolean tracking) {
		_edits = edits;
		_tracking = tracking;
	}

	SpanShift insert(int offset, int count) {
		return append(offset, count);
	}

	SpanShift delete(int offset, int count) {
		return append(offset, -count);
	}

	private SpanShift append(int offset, int delta) {
		if (delta == 0 || !_tracking) {
			return this;
		}
		if (_edits.length / 2 >= MAX_EDITS) {
			return UNTRACKED;
		}
		int[] edits = Arrays.copyOf(_edits, _edits.length + 2);
		edits[_edits.length] = offset;
		edits[_edits.length + 1] = delta;
		return new SpanShift(edits, true);
	}

	/**
	 * Maps the end offset (inclusive) of a span in the lexed text to where
	 * that span ends now. The mapping never decreases, so mapped ends stay
	 * sorted.
	 */
	public int map(int end) {
		int[] edits = _edits;
		for (int i = 0; i < edits.length; i += 2) {
			int offset = edits[i];
			int delta = edits[i + 1];
			if (delta > 0) {
				// text typed right after a token extends that token
				if (end >= offset - 1) {
					end += delta;
				}
			} else if (end >= offset - delta) {
				end += delta;
			} else if (end >= offset) {
				// the span ended inside the deleted range
				end = offset - 1;
			}
		}
		return end;
	}

	/**
	 * Index of the span that colors the char at offset: the first span whose
	 * mapped end is at or after offset, or the last span if there is none.
	 * Span ends must be in non-decreasing order, as the lexers emit them.
	 */
	public static int findSpan(List<Pair> spans, int offset, SpanShift shift) {
		int lo = 0;
		int hi = spans.size() - 1;
		while (lo < hi) {
			int mid = (lo + hi) >>> 1;
			if (shift.map(spans.get(mid).first) >= offset) {
				hi = mid;
			} else {
				lo = mid + 1;
			}
		}
		return Math.max(lo, 0);
	}
}
