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

import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;

public class SimpleDiskSavingLogger extends AbstractFilebasedLogger
{
	final Logger javaLogger;

	public SimpleDiskSavingLogger() {
		this(".");
	}

	public SimpleDiskSavingLogger(final String logFolder) {
		this(logFolder, "log.txt");
	}

	public SimpleDiskSavingLogger(final String logFolder, final String fileName) {
		this(logFolder, fileName, "");
	}

	public SimpleDiskSavingLogger(final String logFolder, final String fileName, final String prefix) {
		super(prefix);
		javaLogger = Logger.getLogger("FuserLog_" + logFolder + "/" + fileName);
		javaLogger.setUseParentHandlers(false);

		final String logFilePath = logFolder + File.separator + fileName;
		try {
			//super.info("Starting new logger: " + logFilePath);
			final FileHandler fh = new FileHandler(logFilePath);
			fh.setFormatter(EASYFORMATTER);
			javaLogger.addHandler(fh);
		} catch (IOException e) {
			System.out.println("Going to be a silent logger because I failed to open the log file.");
			e.printStackTrace();
			//NB: no handler added, the logger should thus remain silent but happy otherwise...
		}
	}

	@Override
	public FilebasedLogger create(final String logFolder, final String fileName, final String prefix) {
		return new SimpleDiskSavingLogger(logFolder, fileName, prefix);
	}


	static public
	Formatter EASYFORMATTER = new Formatter() {
		@Override
		public String format(java.util.logging.LogRecord logRecord) {
			return logRecord.getMessage() + "\n";
		}
	};

	protected void submitThisMsg(final Object origInputMessage, final String finalizedMessage) {
		javaLogger.info(finalizedMessage);
		if (shouldAlsoLeakThis(origInputMessage)) leakingTarget.debug(finalizedMessage);
	}


	@Override
	public void debug(Object msg) {
		final String finalMsg = createMessage("DBG", msg);
		submitThisMsg(msg, finalMsg);
	}

	@Override
	public void error(Object msg) {
		final String finalMsg = createMessage("ERROR", msg);
		submitThisMsg(msg, finalMsg);
	}

	@Override
	public void info(Object msg) {
		final String finalMsg = createMessage("INFO", msg);
		submitThisMsg(msg, finalMsg);
	}

	@Override
	public void trace(Object msg) {
		final String finalMsg = createMessage("TRACE", msg);
		submitThisMsg(msg, finalMsg);
	}

	@Override
	public void warn(Object msg) {
		final String finalMsg = createMessage("WARN", msg);
		submitThisMsg(msg, finalMsg);
	}
}
