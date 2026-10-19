/*
 * Copyright (c) 2013 Tah Wei Hoon.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache License Version 2.0,
 * with full text available at http://www.apache.org/licenses/LICENSE-2.0.html
 *
 * This software is provided "as is". Use at your own risk.
 */
package com.riyadm.codeeditor.util;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Does lexical analysis of a text for C-like languages.
 * The programming language syntax used is set as a static class variable.
 * <p>
 * Lexing runs on a shared background thread. Requests made while editing
 * wait for a short quiet period, and every request makes the queued and
 * running ones stale: those are dropped, so only spans for the latest text
 * reach the document.
 */
public class Lexer {
	private final static int MAX_KEYWORD_LENGTH = 127;

	public final static int UNKNOWN = -1;
	public final static int NORMAL = 0;//обычный текст
	public final static int KEYWORD = 1;//зарезервированные слова
	public final static int OPERATOR = 2;//операторы
	public final static int NAME = 3;
	public final static int LITERAL = 4;//литералы
	public final static int COMMENT = 5;//комментарии
	public final static int SEPARATOR = 6;//разделитель
	public final static int PACKAGE = 7;//пакет
	public final static int TYPE = 8;//тип
	public final static int STRING = 9;//строка
	public final static int ERROR = 10;//ошибка

	/** Quiet period after the last edit before the text is lexed again */
	static final long RELEX_DELAY_MS = 200;

	private static final ScheduledExecutorService mExecutorService;

	static{
		ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1, r -> {
			Thread thread = new Thread(r, "Lexer");
			thread.setDaemon(true);
			return thread;
		});
		// every keystroke cancels the queued lex; don't keep those around
		executor.setRemoveOnCancelPolicy(true);
		mExecutorService = executor;
	}

	private static final Scheduler DEFAULT_SCHEDULER =
			(task, delayMs) -> mExecutorService.schedule(task, delayMs, TimeUnit.MILLISECONDS);

	private LexTask _workerTask;
	private final LexCallback _callback;
	private final Scheduler _scheduler;
	/** Bumped by every request; a lex whose generation is older is stale */
	private final AtomicInteger _generation = new AtomicInteger();
	/** The lex waiting for its delay to pass, guarded by this */
	private Future<?> _pending;
	/** The task lexing on the background thread right now, if any */
	private volatile LexTask _runningTask;

	public Lexer(LexCallback callback, LexTask task) {
		this(callback, task, DEFAULT_SCHEDULER);
	}

	Lexer(LexCallback callback, LexTask task, Scheduler scheduler) {
		_callback = callback;
		_workerTask = task;
		_scheduler = scheduler;
	}

	public synchronized void setWorkerTask(LexTask workerTask) {
		supersede();
		_workerTask = workerTask;
	}

	/** Lexes hDoc once edits have paused for RELEX_DELAY_MS */
	public void tokenize(DocumentProvider hDoc) {
		tokenize(hDoc, false);
	}

	/**
	 * Lexes hDoc in the background. Unless immediate is set, the lex waits
	 * for RELEX_DELAY_MS, and another request in that time replaces it.
	 * A freshly opened text should be lexed immediately, so its colors
	 * show up at once.
	 */
	public synchronized void tokenize(DocumentProvider hDoc, boolean immediate) {
		final int generation = supersede();
		final LexTask task = _workerTask;
		if (!task.getLanguage().isProgLang()) {
			return;
		}

		//tokenize will modify the state of hDoc; make a copy
		final DocumentProvider doc = new DocumentProvider(hDoc);
		_pending = _scheduler.schedule(() -> lex(generation, doc, task),
				immediate ? 0 : RELEX_DELAY_MS);
	}

	public synchronized void cancelTokenize() {
		supersede();
	}

	/**
	 * Makes every queued and running lex stale, and aborts the running one.
	 * Returns the new generation.
	 */
	private int supersede() {
		int generation = _generation.incrementAndGet();
		if (_pending != null) {
			_pending.cancel(false);
			_pending = null;
		}
		LexTask running = _runningTask;
		if (running != null) {
			running.abort();
		}
		return generation;
	}

	/** Runs on the lexer thread */
	private void lex(int generation, DocumentProvider doc, LexTask task) {
		_runningTask = task;
		try {
			// cleared before the staleness check: an abort() sent along with
			// a newer request then can't be lost
			task.abort = false;
			if (generation != _generation.get()) {
				return;
			}
			// copied here rather than on the UI thread, which only waits if
			// it edits during the copy
			TextBuffer.Snapshot snapshot = doc.snapshot();
			List<Pair> tokens = new ArrayList<>();
			task.tokenize(tokens, snapshot.text);
			if (generation != _generation.get()) {
				return;
			}
			if (task.abort) {
				// nobody asked for a newer lex, so the result is still wanted
				retry(generation, doc, task);
				return;
			}
			if (tokens.isEmpty()) {
				return;
			}
			// drawing looks spans up by binary search, which needs sorted ends
			for (int i = 1; i < tokens.size(); ++i) {
				if (tokens.get(i).first < tokens.get(i - 1).first) {
					tokens.get(i).first = tokens.get(i - 1).first;
				}
			}
			if (_callback != null) {
				_callback.lexDone(new Result(this, generation, doc, tokens, snapshot.version));
			}
		} finally {
			_runningTask = null;
		}
	}

	private synchronized void retry(int generation, DocumentProvider doc, LexTask task) {
		if (generation == _generation.get()) {
			_pending = _scheduler.schedule(() -> lex(generation, doc, task), 0);
		}
	}

	/** See Result.apply() */
	boolean apply(Result result) {
		if (result.generation != _generation.get()) {
			return false;
		}
		if (result.doc.setSpans(result.spans, result.version)) {
			return true;
		}
		// the text was edited without a new request; lex it again
		tokenize(result.doc, false);
		return false;
	}

	/** Spans lexed from one version of a text */
	public static final class Result {
		public final List<Pair> spans;
		private final Lexer lexer;
		private final int generation;
		private final DocumentProvider doc;
		private final long version;

		Result(Lexer lexer, int generation, DocumentProvider doc, List<Pair> spans, long version) {
			this.lexer = lexer;
			this.generation = generation;
			this.doc = doc;
			this.spans = spans;
			this.version = version;
		}

		/**
		 * Gives the spans to the document, unless a newer lex was requested
		 * or the text changed since it was read. Call it on the UI thread.
		 * Returns whether the spans were set.
		 */
		public boolean apply() {
			return lexer.apply(this);
		}
	}

	/** Runs tasks after a delay; replaceable for tests */
	interface Scheduler {
		Future<?> schedule(Runnable task, long delayMs);
	}

	public interface LexCallback {
		/** Usually called from a non-UI thread */
		public void lexDone(Result result);
	}
}
