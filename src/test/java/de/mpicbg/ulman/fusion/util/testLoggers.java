/*-
 * #%L
 * label-fusion-ng
 * %%
 * Copyright (C) 2020 - 2026 Vladimír Ulman
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
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
				new RestrictedConsoleLogger(),
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
				new RestrictedConsoleLogger("PrEfIx"),
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
