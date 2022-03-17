package de.mpicbg.ulman.fusion.util;

import de.mpicbg.ulman.fusion.util.loggers.*;
import org.scijava.log.Logger;

public class testLoggers {

	public static void main(String[] args) {
		testWithoutPrefix();
		testWithPrefix();
	}

	static
	void testWithoutPrefix() {
		final Logger[] loggers = {
				new NoOutputLogger(),
				new SimpleConsoleLogger(),
				new SimpleRestrictedLogger(),
				new TimeStampedConsoleLogger(),
				new SimpleDiskSavingLogger()
		};

		useLoggers(loggers);
		testWithSubloggers(loggers);
	}

	static
	void testWithPrefix() {
		final Logger[] loggers = {
				new NoOutputLogger("PrEfIx"),
				new SimpleConsoleLogger("PrEfIx"),
				new SimpleRestrictedLogger("PrEfIx"),
				new TimeStampedConsoleLogger("PrEfIx"),
		};

		useLoggers(loggers);
		testWithSubloggers(loggers);
	}

	static
	void testWithSubloggers(final Logger[] loggers) {
		final Logger[] subLoggers = new Logger[loggers.length];
		for (int i = 0; i < loggers.length; ++i) {
			subLoggers[i] = loggers[i].subLogger("subLogger");
		}

		useLoggers(subLoggers);
	}

	static
	void useLoggers(final Logger[] loggers) {
		for (Logger l : loggers) {
			final String lName = l.getClass().getSimpleName();
			System.out.println("Logging with: "+lName);
			l.trace(lName+" says: trace");
			l.debug(lName+" says: debug");
			l.info(lName+" says: info");
			l.warn(lName+" says: warn");
			l.error(lName+" says: error");
		}
	}
}
