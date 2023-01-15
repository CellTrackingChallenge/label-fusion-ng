/*
 * BSD 2-Clause License
 *
 * Copyright (c) 2020,2022, Vladimír Ulman
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
package de.mpicbg.ulman.fusion.ng.fuse;

import net.imglib2.type.numeric.RealType;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.view.Views;
import net.imglib2.loops.LoopBuilder;
import java.util.Vector;
import de.mpicbg.ulman.fusion.ng.extract.LabelExtractor;

import org.scijava.log.Logger;
import de.mpicbg.ulman.fusion.util.loggers.RestrictedConsoleLogger;

public class WeightedVotingLabelFuser<IT extends RealType<IT>, ET extends RealType<ET>>
implements LabelFuser<IT,ET>
{
	/** convenience (alias) method to set the this.minFractionOfMarker attribute */
	public
	void setMinAcceptableWeight(final double minAcceptableWeight)
	{
		this.minAcceptableWeight = minAcceptableWeight;
	}

	public
	double minAcceptableWeight = 0.01f;

	public static final double FUSION_LABEL = 1.0;

	/**
	 * Input images are cummulated into "a certainty" how strongly a given
	 * voxel should appear in the final fused segment. The output image is
	 * then thresholded with this.minAcceptableWeight, which one should set
	 * beforehand with, e.g., setMinAcceptableWeight(), and is made binary.
	 */
	@Override
	public
	void fuseMatchingLabels(final Vector<RandomAccessibleInterval<IT>> inImgs,
	                        final Vector<Float> inLabels,
	                        final LabelExtractor<IT,?,ET> le,
	                        final Vector<Double> inWeights,
	                        final RandomAccessibleInterval<ET> outImg)
	{
		//the "adding constant" with the weight of an image
		final ET ONE = Views.flatIterable(outImg).firstElement().createVariable();

		for (int i=0; i < inImgs.size(); ++i)
		{
			if (inImgs.get(i) == null) continue;

			//change the "adding constant" to the weight of this image...
			ONE.setReal(inWeights.get(i));
			//...and extract this label into a temporary image
			le.addGivenLabel(inImgs.get(i),inLabels.get(i), outImg,ONE);
		}

		//finalize the current fused segment
		LoopBuilder.setImages(outImg).forEachPixel(
			(a) -> a.setReal( a.getRealFloat() >= minAcceptableWeight ? FUSION_LABEL : 0 ) );
	}

	// ---------------- logging ----------------
	Logger log = new RestrictedConsoleLogger();
	@Override
	public void useNowThisLog(final Logger log)
	{ this.log = log; }
}
