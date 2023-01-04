/*
 * BSD 2-Clause License
 *
 * Copyright (c) 2023, Vladimír Ulman
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package de.mpicbg.ulman.fusion.ng.extract;

import de.mpicbg.ulman.fusion.util.SegGtImageLoader;
import net.imglib2.IterableInterval;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;

import java.util.HashMap;
import java.util.Map;

/**
 * The purpose of this class is to carry over SEG related information for the LabelPicker class.
 * The LabelPicker is a particular implementation of LabelFuser, and it requires to have an access
 * to the relevant SEG image and to have knowledge of which SEG label is _currently_ being processed.
 * This shall be achieved _without_ modifying the service/backend framework around, and this framework
 * is not ready for this. But since the LabelExtractor object is carried over from the generic
 * framework's fusing code (that iteratively calls the LabelPicker), this implementation of the
 * LabelExtractor was hijacked...
 */
public class LabelExtractorForCherryPicker <IT extends RealType<IT>, LT extends IntegerType<LT>, ET extends RealType<ET>>
		extends MajorityOverlapBasedLabelExtractor<IT,LT,ET>
{
	/** Essentially only memorizes which 'markerValue' was used as the last. */
	@Override
	public
	float findMatchingLabel(final IterableInterval<IT> inII,
	                        final IterableInterval<LT> markerII,
	                        final int markerValue)
	{
		lastlyExtractedMarkerValue = markerValue;
		return super.findMatchingLabel(inII,markerII,markerValue);
	}

	/** The value of the most recently processed TRA marker. */
	public int lastlyExtractedMarkerValue = -1;

	/**
	 * Warrants the mapping from the 'lastlyExtractedMarkerValue' to it's relevant
	 * SEG label (which is accessible via the 'segGtImageLoader'). It is being
	 * regularly updated in CherryPicker's wrapper to the fuse() method.
	 */
	public Map<Integer,Integer> traToSegLabelValues = new HashMap<>(200);

	/** Warrants access to the SEG image data. It is initiated in CherryPicker's c'tor. */
	public SegGtImageLoader<LT> segGtImageLoader;
}
