package de.mpicbg.ulman.fusion.util.loggers;

import de.mpicbg.ulman.fusion.Fusers;
import org.scijava.log.LogService;

public interface FilebasedLogger extends LogService {
	/** CMV-tailored way of creating new loggers */
	org.scijava.log.Logger subLogger(final Fusers.OneCombination<?, ?> c);

	/** CMV-tailored way of creating new loggers */
	org.scijava.log.Logger subLogger(final Fusers.OneCombination<?, ?> c,
	                                 final String logFileMarker);

	/** creates a new instance of itself */
	FilebasedLogger create(final String logFolder, final String fileName, final String prefix);
}
