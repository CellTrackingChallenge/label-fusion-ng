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

	@Override
	public void debug(Object msg) {
		final String finalMsg = createMessage("DBG", msg);
		javaLogger.info(finalMsg);
		if (shouldAlsoLeakThis(msg)) leakingTarget.debug(finalMsg);
	}

	@Override
	public void error(Object msg) {
		final String finalMsg = createMessage("ERROR", msg);
		javaLogger.info(finalMsg);
		if (shouldAlsoLeakThis(msg)) leakingTarget.error(finalMsg);
	}

	@Override
	public void info(Object msg) {
		final String finalMsg = createMessage("INFO", msg);
		javaLogger.info(finalMsg);
		if (shouldAlsoLeakThis(msg)) leakingTarget.info(finalMsg);
	}

	@Override
	public void trace(Object msg) {
		final String finalMsg = createMessage("TRACE", msg);
		javaLogger.info(finalMsg);
		if (shouldAlsoLeakThis(msg)) leakingTarget.trace(finalMsg);
	}

	@Override
	public void warn(Object msg) {
		final String finalMsg = createMessage("WARN", msg);
		javaLogger.info(finalMsg);
		if (shouldAlsoLeakThis(msg)) leakingTarget.warn(finalMsg);
	}
}
