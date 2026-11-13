/*-
 * #%L
 * label-fusion-ng
 * %%
 * Copyright (C) 2020 - 2026 Vladimír Ulman
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package de.mpicbg.ulman.fusion.util.loggers;

import java.util.Date;
import java.util.Map;
import java.util.HashMap;

public class ThreadpoolDiskSavingLogger extends AbstractFilebasedLogger implements AutoCloseable
{
	public ThreadpoolDiskSavingLogger() {
		this(".");
	}

	public ThreadpoolDiskSavingLogger(final String logFolder) {
		this(logFolder,"log");
	}

	public ThreadpoolDiskSavingLogger(final String logFolder, final String fileNamePrefix) {
		this(logFolder, fileNamePrefix, "");
	}

	public ThreadpoolDiskSavingLogger(final String logFolder, final String fileNamePrefix, final String prefix) {
		super(prefix);
		this.timeStamp = new Date().toString().replace(" ","-");
		this.logFolder = logFolder;
		this.fileNamePrefix = fileNamePrefix;
		this.loggers = new HashMap<>(32);
		this.masterLogger = new SimpleDiskSavingLogger(logFolder, fileNamePrefix+"_main_"+timeStamp+".txt");
	}

	private ThreadpoolDiskSavingLogger(final ThreadpoolDiskSavingLogger masterInstance, final String prefix) {
		super(prefix);
		this.timeStamp = masterInstance.timeStamp;
		this.logFolder = masterInstance.logFolder;
		this.fileNamePrefix = masterInstance.fileNamePrefix;
		this.loggers = masterInstance.loggers;
		this.masterLogger = null;

		this.doVerboseLogging = masterInstance.doVerboseLogging;
	}

	@Override
	public FilebasedLogger create(final String logFolder, final String fileName, final String prefix) {
		//NB: logFolder and filename is ignored as we re-use already created & opened log files
		return new ThreadpoolDiskSavingLogger(this, prefix);
	}

	private final String timeStamp;
	private final String logFolder;
	private final String fileNamePrefix;
	private final Map<Integer, BufferedDiskSavingLogger> loggers;
	private final SimpleDiskSavingLogger masterLogger;

	private SimpleDiskSavingLogger getLogger() {
		if (masterLogger != null) return masterLogger;

		final int logIdx = (int)Thread.currentThread().getId();
		BufferedDiskSavingLogger l = loggers.get(logIdx);
		if (l == null) {
			l = new BufferedDiskSavingLogger(logFolder, fileNamePrefix+"_thread"+logIdx+"_"+timeStamp+".txt");
			loggers.put(logIdx, l);
		}
		return l;
	}

	@Override
	public void close() {
		//only the master instance closes the individual loggers
		if (masterLogger == null) return;
		for (BufferedDiskSavingLogger l : loggers.values()) l.close();
	}


	private boolean doVerboseLogging = false;

	public ThreadpoolDiskSavingLogger enableVerboseLogging() {
		doVerboseLogging = true;
		return this;
	}
	public ThreadpoolDiskSavingLogger disableVerboseLogging() {
		doVerboseLogging = false;
		return this;
	}


	@Override
	public void trace(Object msg) {
		if (doVerboseLogging) {
			final String finalMsg = createMessage("TRACE", msg);
			getLogger().submitThisMsg(msg, finalMsg);
		}
	}

	@Override
	public void debug(Object msg) {
		if (doVerboseLogging) {
			final String finalMsg = createMessage("DEBUG", msg);
			getLogger().submitThisMsg(msg, finalMsg);
		}
	}

	@Override
	public void info(Object msg) {
		final String finalMsg = createMessage("INFO", msg);
		getLogger().submitThisMsg(msg, finalMsg);
	}

	@Override
	public void warn(Object msg) {
		final String finalMsg = createMessage("WARN", msg);
		getLogger().submitThisMsg(msg, finalMsg);
	}

	@Override
	public void error(Object msg) {
		final String finalMsg = createMessage("ERROR", msg);
		getLogger().submitThisMsg(msg, finalMsg);
	}
}
