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
