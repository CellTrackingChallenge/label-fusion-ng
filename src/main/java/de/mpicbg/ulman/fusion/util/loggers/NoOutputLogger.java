package de.mpicbg.ulman.fusion.util.loggers;

import org.scijava.log.Logger;

public class NoOutputLogger extends SimpleConsoleLogger
{
	public NoOutputLogger() { super(); }
	public NoOutputLogger(final String name) { super(); }

	@Override
	public Logger subLogger(String name, int level) {
		return new NoOutputLogger(name);
	}

	@Override
	public void debug(Object msg) {};

	@Override
	public void error(Object msg) {};

	@Override
	public void info(Object msg) {};

	@Override
	public void trace(Object msg) {};

	@Override
	public void warn(Object msg) {};
}
