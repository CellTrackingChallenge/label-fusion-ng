package de.mpicbg.ulman.fusion.util.loggers;

public class RestrictedDiskSavingLogger extends SimpleDiskSavingLogger
{
	public RestrictedDiskSavingLogger() {
		super();
	}

	public RestrictedDiskSavingLogger(final String logFolder) {
		super(logFolder);
	}

	public RestrictedDiskSavingLogger(final String logFolder, final String fileName) {
		super(logFolder, fileName);
	}

	public RestrictedDiskSavingLogger(final String logFolder, final String fileName, final String prefix) {
		super(logFolder, fileName, prefix);
	}

	@Override
	public FilebasedLogger create(final String logFolder, final String fileName, final String prefix) {
		return new RestrictedDiskSavingLogger(logFolder, fileName, prefix);
	}


	@Override
	public void debug(Object msg) { /* empty */ }

	@Override
	public void trace(Object msg) { /* empty); */ }
}
