/*
 * BSD 2-Clause License
 *
 * Copyright (c) 2020, Vladimír Ulman
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

import net.imglib2.Cursor;
import net.imglib2.type.numeric.RealType; import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import java.util.Vector;
import de.mpicbg.ulman.fusion.ng.extract.LabelExtractor;

public class ForcedFlatVotingLabelFuserWithFailSafe<IT extends RealType<IT>, ET extends RealType<ET>>
extends WeightedVotingLabelFuser<IT,ET>
implements LabelFuser<IT,ET>
{
	/**
	 * Input images are cummulated into "a certainty" how strongly a given
	 * voxel should appear in the final fused segment. Despite the weights for the
	 * inputs are given, here **every input is treated with the same (flat) weight**.
	 * The output image is then thresholded with this.minAcceptableWeight, which one
	 * should set beforehand with, e.g., setMinAcceptableWeight(), and is made binary.
	 *
	 * This process may fail, e.g., when there is not enough images to accumulate
	 * over the threshold, which is detected and in which case the "emergency"
	 * (fail safe) routine is executed.
	 * Here, the {@link FailSafeInsertor#clearAndInsertBestWeightMarker} is used
	 * which **will use the weights**. So the weights are considered only for the
	 * fail safe operation, and are **not** considered during the voting itself.
	 */
	@Override
	public
	void fuseMatchingLabels(final Vector<RandomAccessibleInterval<IT>> inImgs,
	                        final Vector<Float> inLabels,
	                        final LabelExtractor<IT,?,ET> le,
	                        final Vector<Double> inWeights,
	                        final Img<ET> outImg)
	{
		//prepare aux flat weights
		while (flatWeightsCache.size() < inWeights.size()) flatWeightsCache.add(1.0);
		while (flatWeightsCache.size() > inWeights.size()) flatWeightsCache.removeElementAt(flatWeightsCache.size()-1);

		super.fuseMatchingLabels(inImgs,inLabels, le, flatWeightsCache,outImg);

		//check if fusion managed to create something
		boolean isEmpty = true;
		final Cursor<ET> oC = outImg.cursor();
		while (isEmpty && oC.hasNext())
			isEmpty = oC.next().getRealFloat() == 0;

		if (isEmpty)
		{
			System.out.print("FSed ");
			FailSafeInsertor.clearAndInsertBestWeightMarker(inImgs,inLabels, le, inWeights,outImg);
		}
	}

	Vector<Double> flatWeightsCache = new Vector<>(30);
}
