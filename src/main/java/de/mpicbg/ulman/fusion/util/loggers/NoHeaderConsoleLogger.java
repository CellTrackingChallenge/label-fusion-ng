package de.mpicbg.ulman.fusion.util.loggers;

import org.scijava.log.Logger;

public class NoHeaderConsoleLogger extends SimpleConsoleLogger
{
	public NoHeaderConsoleLogger() { super(); }
	public NoHeaderConsoleLogger(final String name) { super(name); }

	@Override
	public Logger subLogger(String name, int level) {
		return new NoHeaderConsoleLogger(name);
	}

	@Override
	String createMessage(final String reportedLevel, final Object message) {
		return message.toString();
	}
}