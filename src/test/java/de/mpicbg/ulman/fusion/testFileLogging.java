package de.mpicbg.ulman.fusion;

import de.mpicbg.ulman.fusion.ng.backbones.WeightedVotingFusionFeeder;
import de.mpicbg.ulman.fusion.util.loggers.SimpleDiskSavingLogger;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import org.scijava.log.Logger;

import java.io.IOException;

public class testFileLogging {
	public static void main(String[] args) {
		SimpleDiskSavingLogger mainLog
				= new SimpleDiskSavingLogger(".");
		mainLog.info("Hi, me da main");
		mainLog.info("You? No!");
		mainLog.warn("I'm the main here!");

		WeightedVotingFusionFeeder<UnsignedShortType,UnsignedShortType> feeder
				= new WeightedVotingFusionFeeder<>(mainLog);

		Fusers.OneCombination<UnsignedShortType,UnsignedShortType> c1
				= new Fusers().new OneCombination<>(2,1,3);
		Fusers.OneCombination<UnsignedShortType,UnsignedShortType> c2
				= new Fusers().new OneCombination<>(5,2,14);
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

		c1Log.info("c1 \"info-ing\" here!");
		c2Log.error("c2 \"error-ing\" here!");
	}
}
