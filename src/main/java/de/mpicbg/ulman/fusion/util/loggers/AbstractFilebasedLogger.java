package de.mpicbg.ulman.fusion.util.loggers;

import de.mpicbg.ulman.fusion.Fusers;

import java.util.HashSet;
import java.util.Set;

public abstract class AbstractFilebasedLogger extends TimeStampedConsoleLogger implements FilebasedLogger
{
	protected AbstractFilebasedLogger(final String prefix) {
		super(prefix);
	}


	private final static String NOMARKER = "";

	@Override
	public org.scijava.log.Logger subLogger(final Fusers.OneCombination<?, ?> c) {
		return this.subLogger(c, NOMARKER);
	}

	@Override
	public org.scijava.log.Logger subLogger(final Fusers.OneCombination<?, ?> c,
	                                        final String logFileMarker) {
		return create(c.logFolder, "log_" + c.code + logFileMarker + ".txt", c.code + " ");
	}


	protected org.scijava.log.Logger leakingTarget = null;
	final private Set<String> leakersPatterns = new HashSet<>(10);

	public void setLeakingTarget(final org.scijava.log.Logger newLeaksTarget) {
		leakingTarget = newLeaksTarget;
	}

	public void leakAlsoThese(final String leakPatternInsideMsg) {
		leakersPatterns.add( leakPatternInsideMsg );
	}

	protected boolean shouldAlsoLeakThis(final Object msg) {
		if (leakingTarget == null) return false;

		final String msgStr = msg.toString();
		for (String pattern : leakersPatterns)
			if (msgStr.contains(pattern)) return true;
		return false;
	}
}