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
package de.mpicbg.ulman.fusion;

import de.mpicbg.ulman.fusion.ng.backbones.WeightedVotingFusionFeeder;
import de.mpicbg.ulman.fusion.util.loggers.FilebasedLogger;
import de.mpicbg.ulman.fusion.util.loggers.SimpleConsoleLogger;
import de.mpicbg.ulman.fusion.util.loggers.SimpleDiskSavingLogger;
import de.mpicbg.ulman.fusion.util.loggers.RestrictedDiskSavingLogger;
import de.mpicbg.ulman.fusion.util.loggers.ThreadpoolDiskSavingLogger;
import de.mpicbg.ulman.fusion.util.loggers.BufferedDiskSavingLogger;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import org.scijava.log.Logger;

import java.io.IOException;

public class testFileLogging {
	public static void main(String[] args) {
		testThisLogger( new SimpleDiskSavingLogger(".", "simple_LLL.log") );
		testThisLogger( new RestrictedDiskSavingLogger(".", "restricted_LLL.log") );
		testThisLogger( new ThreadpoolDiskSavingLogger(".", "threadLogger") );
		testThisLogger( new ThreadpoolDiskSavingLogger(".", "threadLogger_verbose").enableVerboseLogging() );
		try (final BufferedDiskSavingLogger bufLog = new BufferedDiskSavingLogger(".", "bufferedLogger.txt")) {
			testThisLogger( bufLog );
		}
	}

	public static void testThisLogger(final FilebasedLogger mainLog) {
		mainLog.info("Hi, me da main");
		mainLog.info("You?");
		mainLog.trace("(after some thinking...)");
		mainLog.warn("No! I'm the main here!");

		WeightedVotingFusionFeeder<UnsignedShortType,UnsignedShortType> feeder
				= new WeightedVotingFusionFeeder<>(mainLog);

		final Fusers f = new Fusers();
		f.log = new SimpleConsoleLogger();

		Fusers.OneCombination<UnsignedShortType,UnsignedShortType> c1
				= f.new OneCombination<>(2,1,3);
		Fusers.OneCombination<UnsignedShortType,UnsignedShortType> c2
				= f.new OneCombination<>(5,2,14);
		try {
			c1.feeder = feeder;
			c2.feeder = feeder;
			c1.setupOutputFolder("./outMasks.tif");
			c2.setupOutputFolder("./outMasks.tif");
		} catch (IOException e) {
			e.printStackTrace();
		}

		Logger c1Log = mainLog.subLogger(c1);
		Logger c2Log = mainLog.subLogger(c2);

		c1Log.trace("c1 \"trace-ing\" here!");
		c1Log.info("c1 \"info-ing\" here!");
		c2Log.error("c2 \"error-ing\" here!");
	}
}
