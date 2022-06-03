package de.mpicbg.ulman.fusion.util.loggers;

import java.util.List;
import java.util.LinkedList;

public class BufferedDiskSavingLogger extends SimpleDiskSavingLogger implements AutoCloseable
{
	public BufferedDiskSavingLogger() {
		super(".");
	}

	public BufferedDiskSavingLogger(final String logFolder) {
		super(logFolder);
	}

	public BufferedDiskSavingLogger(final String logFolder, final String fileName) {
		super(logFolder, fileName);
	}

	public BufferedDiskSavingLogger(final String logFolder, final String fileName, final String prefix) {
		super(logFolder, fileName, prefix);
	}

	@Override
	public FilebasedLogger create(final String logFolder, final String fileName, final String prefix) {
		BufferedDiskSavingLogger l = new BufferedDiskSavingLogger(logFolder, fileName, prefix);
		additionalCloseables.add(l);
		return l;
	}

	private final List<BufferedDiskSavingLogger> additionalCloseables = new LinkedList<>();


	@Override
	protected void submitThisMsg(final Object origInputMessage, final String finalizedMessage) {
		if (buffer.length() > bufferFlushThres) flush();
		buffer.append(finalizedMessage);
		buffer.append('\n');
		if (shouldAlsoLeakThis(origInputMessage)) leakingTarget.debug(finalizedMessage);
	}

	private final StringBuffer buffer = new StringBuffer(1024 * 1024); //1 MB
	private final int bufferFlushThres = (int)(0.95 * buffer.capacity());

	public void flush() {
		javaLogger.info(buffer.toString());
		buffer.setLength(0);
	}

	@Override
	public void close() {
		flush();
		additionalCloseables.forEach(bufLogger -> {
			try {
				bufLogger.close();
			}
			catch (Exception e) {
				System.out.println("Failing to close the buffered logger '"+bufLogger.prefix+"': "+e.getMessage());
			}
		});
	}
}
