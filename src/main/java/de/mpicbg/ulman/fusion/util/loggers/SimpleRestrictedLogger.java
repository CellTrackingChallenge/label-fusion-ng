package de.mpicbg.ulman.fusion.util.loggers;

import org.scijava.log.Logger;

public class SimpleRestrictedLogger extends SimpleConsoleLogger
{
	public SimpleRestrictedLogger() {
		super();
	}

	SimpleRestrictedLogger(final String prefix) {
		super(prefix);
	}

	@Override //NB: fork into itself again (to preserve the verbosity level)
	public Logger subLogger(String name, int level) {
		return new SimpleRestrictedLogger(name);
	}

	@Override
	public void debug(Object msg) { /* empty */ }

	@Override
	public void trace(Object msg) { /* empty); */ }
}
