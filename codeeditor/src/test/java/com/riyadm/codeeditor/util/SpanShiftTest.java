package com.riyadm.codeeditor.util;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SpanShiftTest {

	/** The span lookup the draw code used before: a scan from index 0 */
	private static int legacyFind(List<Pair> spans, int currentIndex) {
		int spanIndex = 0;
		Pair nextSpan = spans.get(spanIndex++);
		int spanOffset = 0;
		int found;
		do {
			found = spanIndex - 1;
			if (spanIndex < spans.size()) {
				spanOffset = nextSpan.getFirst();
				nextSpan = spans.get(spanIndex++);
			} else {
				nextSpan = null;
			}
		}
		while (nextSpan != null && spanOffset <= currentIndex);
		return found;
	}

	/** First span whose mapped end is at or after offset, else the last span */
	private static int linearFind(List<Pair> spans, int offset, SpanShift shift) {
		for (int i = 0; i < spans.size(); ++i) {
			if (shift.map(spans.get(i).first) >= offset) {
				return i;
			}
		}
		return spans.size() - 1;
	}

	private static List<Pair> randomSpans(Random random) {
		List<Pair> spans = new ArrayList<>();
		int size = 1 + random.nextInt(random.nextBoolean() ? 5 : 300);
		int end = random.nextInt(3) - 1;
		for (int i = 0; i < size; ++i) {
			// zero steps make duplicate ends, as empty tokens do
			end += random.nextInt(6);
			spans.add(new Pair(end, random.nextInt(11)));
		}
		return spans;
	}

	private static SpanShift randomShift(Random random, int length) {
		SpanShift shift = SpanShift.NONE;
		int edits = random.nextInt(8);
		for (int i = 0; i < edits; ++i) {
			int offset = random.nextInt(length + 1);
			int count = 1 + random.nextInt(10);
			if (random.nextBoolean()) {
				shift = shift.insert(offset, count);
			} else {
				shift = shift.delete(offset, Math.min(count, length - offset + 1));
				length = Math.max(0, length - count);
			}
			length += count;
		}
		return shift;
	}

	@Test
	public void binarySearchMatchesLinearScan() {
		Random random = new Random(1);
		for (int round = 0; round < 3000; ++round) {
			List<Pair> spans = randomSpans(random);
			int last = spans.get(spans.size() - 1).first;
			SpanShift shift = round % 2 == 0 ? SpanShift.NONE : randomShift(random, last + 1);
			for (int offset = -2; offset <= last + 15; ++offset) {
				assertEquals("round " + round + " offset " + offset,
						linearFind(spans, offset, shift),
						SpanShift.findSpan(spans, offset, shift));
			}
		}
	}

	/**
	 * The old scan agrees except where a row starts on the last char of a
	 * span: it skipped that span and colored the char like the next one.
	 */
	@Test
	public void binarySearchMatchesLegacyScanAwayFromSpanEnds() {
		Random random = new Random(2);
		for (int round = 0; round < 3000; ++round) {
			List<Pair> spans = randomSpans(random);
			Set<Integer> ends = new HashSet<>();
			for (Pair span : spans) {
				ends.add(span.first);
			}
			int last = spans.get(spans.size() - 1).first;
			for (int offset = 0; offset <= last + 15; ++offset) {
				if (ends.contains(offset)) {
					continue;
				}
				assertEquals("round " + round + " offset " + offset,
						legacyFind(spans, offset),
						SpanShift.findSpan(spans, offset, SpanShift.NONE));
			}
		}
	}

	@Test
	public void mapNeverDecreases() {
		Random random = new Random(3);
		for (int round = 0; round < 2000; ++round) {
			SpanShift shift = randomShift(random, 200);
			int previous = Integer.MIN_VALUE;
			for (int end = -1; end < 260; ++end) {
				int mapped = shift.map(end);
				assertTrue(mapped >= previous);
				previous = mapped;
			}
		}
	}

	@Test
	public void insertAndDeleteMoveEnds() {
		assertEquals(5, SpanShift.NONE.insert(10, 3).map(5));
		// a span ending right before the insertion grows over the new text
		assertEquals(12, SpanShift.NONE.insert(10, 3).map(9));
		assertEquals(13, SpanShift.NONE.insert(10, 3).map(10));
		assertEquals(9, SpanShift.NONE.delete(10, 3).map(9));
		assertEquals(9, SpanShift.NONE.delete(10, 3).map(11));
		assertEquals(10, SpanShift.NONE.delete(10, 3).map(13));
	}

	@Test
	public void tooManyEditsStopsShifting() {
		SpanShift shift = SpanShift.NONE;
		for (int i = 0; i < SpanShift.MAX_EDITS; ++i) {
			shift = shift.insert(0, 1);
		}
		assertEquals(100 + SpanShift.MAX_EDITS, shift.map(100));
		shift = shift.insert(0, 1);
		assertEquals(100, shift.map(100));
		assertEquals(100, shift.delete(0, 5).map(100));
	}

	// --- colors of the chars an edit didn't touch stay right ---

	private static int charClass(char c) {
		if (Character.isLetter(c)) return 1;
		if (Character.isDigit(c)) return 2;
		if (c == ' ' || c == '\n') return 3;
		return 4;
	}

	/** Tokens are runs of one char class; the type is the class */
	private static List<Pair> lex(CharSequence text) {
		List<Pair> spans = new ArrayList<>();
		for (int i = 0; i < text.length(); ++i) {
			if (i + 1 == text.length() || charClass(text.charAt(i + 1)) != charClass(text.charAt(i))) {
				spans.add(new Pair(i, charClass(text.charAt(i))));
			}
		}
		if (spans.isEmpty()) {
			spans.add(new Pair(0, Lexer.NORMAL));
		}
		return spans;
	}

	private static String randomText(Random random, int length) {
		String alphabet = "abc12 .\n";
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < length; ++i) {
			sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
		}
		return sb.toString();
	}

	private static int colorAt(List<Pair> spans, SpanShift shift, int offset) {
		return spans.get(SpanShift.findSpan(spans, offset, shift)).second;
	}

	@Test
	public void untouchedCharsKeepTheirColorThroughBufferEdits() {
		Random random = new Random(4);
		for (int round = 0; round < 300; ++round) {
			String original = randomText(random, 1 + random.nextInt(120));
			Document doc = new Document(null);
			doc.setText(original);
			List<Pair> spans = lex(original);
			doc.setSpans(spans);
			// what each char of the buffer was in the lexed text, -1 if typed since
			List<Integer> origins = new ArrayList<>();
			for (int i = 0; i < original.length(); ++i) {
				origins.add(i);
			}

			int edits = 1 + random.nextInt(SpanShift.MAX_EDITS);
			for (int e = 0; e < edits; ++e) {
				int length = origins.size();
				if (length > 0 && random.nextBoolean()) {
					int offset = random.nextInt(length);
					int count = 1 + random.nextInt(Math.min(8, length - offset));
					doc.delete(offset, count, 0, false);
					origins.subList(offset, offset + count).clear();
				} else {
					int offset = random.nextInt(length + 1);
					char[] typed = randomText(random, 1 + random.nextInt(5)).toCharArray();
					doc.insert(typed, offset, 0, false);
					for (int i = 0; i < typed.length; ++i) {
						origins.add(offset, -1);
					}
				}
			}

			SpanShift shift = doc.getSpanShift();
			assertEquals(doc.toString().length(), origins.size());
			for (int i = 0; i < origins.size(); ++i) {
				int origin = origins.get(i);
				if (origin >= 0) {
					assertEquals("round " + round + " char " + i,
							charClass(original.charAt(origin)), colorAt(spans, shift, i));
				}
			}
		}
	}

	@Test
	public void undoOfTypingRestoresColors() {
		String text = "foo 12 bar.baz\nqux";
		Document doc = new Document(null);
		doc.setText(text);
		List<Pair> spans = lex(text);
		doc.setSpans(spans);

		DocumentProvider provider = new DocumentProvider(doc);
		long time = 1000;
		provider.insertBefore("xyz".toCharArray(), 4, time);
		provider.insertBefore('!', 9, time + 1);
		provider.deleteAt(0, 2, time + 2);
		while (provider.canUndo()) {
			provider.undo();
		}
		assertEquals(text, provider.toString());
		SpanShift shift = doc.getSpanShift();
		for (int i = 0; i < text.length(); ++i) {
			assertEquals("char " + i, charClass(text.charAt(i)), colorAt(spans, shift, i));
		}
	}
}
