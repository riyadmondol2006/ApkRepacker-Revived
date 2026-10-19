package com.riyadm.codeeditor.util;

import com.riyadm.codeeditor.lang.Language;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class LexerTest {

	/** Runs scheduled tasks only when the test moves its clock */
	private static class FakeScheduler implements Lexer.Scheduler {
		final List<Job> jobs = new ArrayList<>();
		long now;

		@Override
		public Future<?> schedule(Runnable task, long delayMs) {
			Job job = new Job(task, now + delayMs);
			jobs.add(job);
			return job;
		}

		void advance(long ms) {
			long target = now + ms;
			while (true) {
				Job next = null;
				for (Job job : jobs) {
					if (!job.cancelled && job.due <= target && (next == null || job.due < next.due)) {
						next = job;
					}
				}
				if (next == null) {
					break;
				}
				jobs.remove(next);
				now = Math.max(now, next.due);
				next.task.run();
			}
			now = target;
		}

		int queued() {
			int count = 0;
			for (Job job : jobs) {
				if (!job.cancelled) {
					++count;
				}
			}
			return count;
		}
	}

	private static class Job implements Future<Object> {
		final Runnable task;
		final long due;
		boolean cancelled;

		Job(Runnable task, long due) {
			this.task = task;
			this.due = due;
		}

		@Override
		public boolean cancel(boolean mayInterruptIfRunning) {
			cancelled = true;
			return true;
		}

		@Override
		public boolean isCancelled() {
			return cancelled;
		}

		@Override
		public boolean isDone() {
			return cancelled;
		}

		@Override
		public Object get() {
			return null;
		}

		@Override
		public Object get(long timeout, TimeUnit unit) {
			return null;
		}
	}

	private static class ProgLanguage extends Language {
	}

	/** One span per char; records what it was asked to lex */
	private static class RecordingTask extends LexTask {
		final List<String> texts = new ArrayList<>();
		final List<Boolean> aborted = new ArrayList<>();
		/** Called once per char, as if the UI thread acted mid-lex */
		Runnable midLex;

		RecordingTask() {
			super(new ProgLanguage());
		}

		@Override
		protected void tokenize(List<Pair> _tokens, String text) {
			texts.add(text);
			for (int i = 0; i < text.length() && !abort; ++i) {
				_tokens.add(new Pair(i, Lexer.NORMAL));
				if (midLex != null) {
					Runnable r = midLex;
					midLex = null;
					r.run();
				}
			}
			aborted.add(abort);
		}

		@Override
		public String getLanguageType() {
			return "Test";
		}
	}

	private FakeScheduler scheduler;
	private RecordingTask task;
	private List<Lexer.Result> results;
	private Lexer lexer;
	private DocumentProvider doc;

	@Before
	public void setUp() {
		scheduler = new FakeScheduler();
		task = new RecordingTask();
		results = new ArrayList<>();
		lexer = new Lexer(results::add, task, scheduler);
		Document document = new Document(null);
		document.setText("hello");
		doc = new DocumentProvider(document);
		doc.clearSpans();
	}

	@Test
	public void openedTextIsLexedWithoutDelay() {
		lexer.tokenize(doc, true);
		scheduler.advance(0);
		assertEquals(1, task.texts.size());
		assertEquals("hello", task.texts.get(0));
		assertEquals(1, results.size());
		assertTrue(results.get(0).apply());
		assertSame(results.get(0).spans, doc.getSpans());
	}

	@Test
	public void rapidEditsGiveOneLexOfTheLatestText() {
		lexer.tokenize(doc, true);
		scheduler.advance(0);
		results.get(0).apply();
		task.texts.clear();
		results.clear();

		for (int i = 0; i < 10; ++i) {
			doc.insertBefore((char) ('a' + i), doc.docLength() - 1, i);
			lexer.tokenize(doc);
			scheduler.advance(Lexer.RELEX_DELAY_MS / 2);
		}
		assertEquals(0, task.texts.size());
		assertEquals(1, scheduler.queued());

		scheduler.advance(Lexer.RELEX_DELAY_MS);
		assertEquals(1, task.texts.size());
		assertEquals("helloabcdefghij", task.texts.get(0));
		assertEquals(doc.toString(), task.texts.get(0));
		assertEquals(1, results.size());
		assertTrue(results.get(0).apply());
		assertSame(results.get(0).spans, doc.getSpans());
		assertEquals(0, scheduler.queued());
	}

	@Test
	public void newerRequestAbortsTheRunningLex() {
		task.midLex = () -> {
			doc.insertBefore('!', 0, 0);
			lexer.tokenize(doc);
		};
		lexer.tokenize(doc, true);
		scheduler.advance(0);
		assertEquals(1, task.texts.size());
		assertTrue(task.aborted.get(0));
		assertTrue(results.isEmpty());

		scheduler.advance(Lexer.RELEX_DELAY_MS);
		assertEquals(2, task.texts.size());
		assertEquals("!hello", task.texts.get(1));
		assertFalse(task.aborted.get(1));
		assertEquals(1, results.size());
		assertTrue(results.get(0).apply());
	}

	@Test
	public void staleResultIsNotApplied() {
		lexer.tokenize(doc, true);
		scheduler.advance(0);
		List<Pair> before = doc.getSpans();
		// a newer request is made before the UI thread gets to the result
		doc.insertBefore('!', 0, 0);
		lexer.tokenize(doc);
		assertFalse(results.get(0).apply());
		assertSame(before, doc.getSpans());

		scheduler.advance(Lexer.RELEX_DELAY_MS);
		assertEquals(2, results.size());
		assertTrue(results.get(1).apply());
		assertEquals("!hello", task.texts.get(1));
	}

	@Test
	public void resultForOldTextIsDroppedAndRelexed() {
		lexer.tokenize(doc, true);
		scheduler.advance(0);
		List<Pair> before = doc.getSpans();
		// edited without a request, e.g. from another thread
		doc.insertBefore('!', 0, 0);
		assertFalse(results.get(0).apply());
		assertSame(before, doc.getSpans());
		assertEquals(1, scheduler.queued());

		scheduler.advance(Lexer.RELEX_DELAY_MS);
		assertEquals("!hello", task.texts.get(1));
		assertTrue(results.get(1).apply());
	}

	@Test
	public void cancelDropsQueuedAndFinishedLexes() {
		lexer.tokenize(doc, true);
		scheduler.advance(0);
		lexer.tokenize(doc);
		lexer.cancelTokenize();
		assertFalse(results.get(0).apply());
		scheduler.advance(Lexer.RELEX_DELAY_MS * 2);
		assertEquals(1, task.texts.size());
	}

	@Test
	public void switchingTaskDropsTheOldOne() {
		lexer.tokenize(doc);
		RecordingTask other = new RecordingTask();
		lexer.setWorkerTask(other);
		lexer.tokenize(doc, true);
		scheduler.advance(Lexer.RELEX_DELAY_MS * 2);
		assertEquals(0, task.texts.size());
		assertEquals(1, other.texts.size());
		assertEquals(1, results.size());
		assertEquals("hello", other.texts.get(0));
	}

	@Test
	public void snapshotMatchesTextAcrossTheGap() {
		Document document = new Document(null);
		document.setText("abcdef");
		DocumentProvider provider = new DocumentProvider(document);
		long version = provider.getVersion();
		provider.insertBefore("XY".toCharArray(), 3, 0);
		provider.deleteAt(0, 0);
		TextBuffer.Snapshot snapshot = provider.snapshot();
		assertEquals("bcXYdef", snapshot.text);
		assertEquals(provider.toString(), snapshot.text);
		assertEquals(version + 2, snapshot.version);
		assertFalse(provider.setSpans(new ArrayList<>(), version));
		assertTrue(provider.setSpans(new ArrayList<>(), snapshot.version));
	}
}
