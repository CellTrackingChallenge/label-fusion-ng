package de.mpicbg.ulman.fusion.util.loggers;

import java.util.Date;
import org.scijava.log.Logger;

public class TimeStampedConsoleLogger extends SimpleConsoleLogger
{
	public TimeStampedConsoleLogger() { super(); }
	public TimeStampedConsoleLogger(final String name) { super(name); }

	@Override
	public Logger subLogger(String name, int level) {
		return new TimeStampedConsoleLogger(name);
	}

	private final Date d = new Date();

	@Override
	synchronized
	String createMessage(final String reportedLevel, final Object message) {
		d.setTime(System.currentTimeMillis());
		return prefix + "[" + d + " " + reportedLevel + "] " + message;
	}
}
