package de.mpicbg.ulman.fusion.util.loggers;

import de.mpicbg.ulman.fusion.Fusers;
import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;

public class SimpleDiskSavingLogger extends TimeStampedConsoleLogger
{
	String prefix = "";
	final Logger javaLogger;

	public SimpleDiskSavingLogger() {
		this(".");
	}

	public SimpleDiskSavingLogger(final String logFolder) {
		this(logFolder, "log.txt");
	}

	SimpleDiskSavingLogger(final String logFolder, final String fileName) {
		super();
		javaLogger = Logger.getLogger("FuserLog_" + logFolder + "/" + fileName);
		javaLogger.setUseParentHandlers(false);

		final String logFilePath = logFolder + File.separator + fileName;
		try {
			super.info("Starting new logger: " + logFilePath);
			final FileHandler fh = new FileHandler(logFilePath);
			fh.setFormatter(EASYFORMATTER);
			javaLogger.addHandler(fh);
		} catch (IOException e) {
			System.out.println("Going to be a silent logger because I failed to open the log file.");
			e.printStackTrace();
			//NB: no handler added, the logger should thus remain silent but happy otherwise...
		}
	}

	public org.scijava.log.Logger subLogger(final Fusers.OneCombination<?, ?> c) {
		SimpleDiskSavingLogger l =
				new SimpleDiskSavingLogger(c.logFolder, "log_" + c.code + ".txt");
		l.prefix = c.code + " ";
		return l;
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
		javaLogger.info( createMessage("DBG", msg) );
	}

	@Override
	public void error(Object msg) {
		javaLogger.info( createMessage("ERROR", msg) );
	}

	@Override
	public void info(Object msg) {
		javaLogger.info( createMessage("INFO", msg) );
	}

	@Override
	public void trace(Object msg) {
		javaLogger.info( createMessage("TRACE", msg) );
	}

	@Override
	public void warn(Object msg) {
		javaLogger.info( createMessage("WARN", msg) );
	}
}
