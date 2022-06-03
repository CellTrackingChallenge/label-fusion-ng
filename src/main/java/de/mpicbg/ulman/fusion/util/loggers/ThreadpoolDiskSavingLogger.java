package de.mpicbg.ulman.fusion.util.loggers;

import java.util.Date;
import java.util.Map;
import java.util.HashMap;

public class ThreadpoolDiskSavingLogger extends AbstractFilebasedLogger
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
		this.masterLogger = new SimpleDiskSavingLogger(logFolder, fileNamePrefix+"_"+timeStamp+".txt");
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
	private final Map<Integer, SimpleDiskSavingLogger> loggers;
	private final SimpleDiskSavingLogger masterLogger;

	private SimpleDiskSavingLogger getLogger() {
		if (masterLogger != null) return masterLogger;

		final int logIdx = (int)Thread.currentThread().getId();
		SimpleDiskSavingLogger l = loggers.get(logIdx);
		if (l == null) {
			l = new SimpleDiskSavingLogger(logFolder, fileNamePrefix+"_thread"+logIdx+"_"+timeStamp+".txt");
			loggers.put(logIdx, l);
		}
		return l;
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