package de.mpicbg.ulman.fusion;

import de.mpicbg.ulman.fusion.ng.backbones.WeightedVotingFusionFeeder;
import de.mpicbg.ulman.fusion.util.loggers.FilebasedLogger;
import de.mpicbg.ulman.fusion.util.loggers.RestrictedDiskSavingLogger;
import de.mpicbg.ulman.fusion.util.loggers.SimpleConsoleLogger;
import de.mpicbg.ulman.fusion.util.loggers.SimpleDiskSavingLogger;
import de.mpicbg.ulman.fusion.util.loggers.ThreadpoolDiskSavingLogger;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import org.scijava.log.Logger;

import java.io.IOException;

public class testFileLogging {
	public static void main(String[] args) {
		testThisLogger( new SimpleDiskSavingLogger(".", "simple_LLL.log") );
		testThisLogger( new RestrictedDiskSavingLogger(".", "restricted_LLL.log") );
		testThisLogger( new ThreadpoolDiskSavingLogger(".", "threadLogger") );
		testThisLogger( new ThreadpoolDiskSavingLogger(".", "threadLogger_verbose").enableVerboseLogging() );
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
