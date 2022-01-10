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

import de.mpicbg.ulman.fusion.ng.extract.LabelExtractor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.view.Views;
import net.imglib2.loops.LoopBuilder;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.operators.SetZero;

import java.util.Vector;

public class FailSafeInsertor
{
	public static
	int getBestWeightIndex(final Vector<?> inImgs,
	                       final Vector<Double> inWeights)
	{
		double bestWeight = -1;
		int    bestInput = -1;

		for (int i=0; i < inImgs.size(); ++i)
		{
			if (inImgs.get(i) == null) continue;

			if (inWeights.get(i) > bestWeight)
			{
				bestWeight = inWeights.get(i);
				bestInput  = i;
			}
		}

		return bestInput;
	}


	public static
	<IT extends RealType<IT>, ET extends RealType<ET>>
	void clearAndInsertGivenMarker(final RandomAccessibleInterval<IT> inImg,
	                               final float inLabel,
	                               final LabelExtractor<IT,?,ET> le,
	                               final double outValue,
	                               final RandomAccessibleInterval<ET> outImg)
	{
		final ET outputValue = Views.flatIterable(outImg).firstElement().createVariable();
		outputValue.setReal( outValue );

		//clear...
		LoopBuilder.setImages(outImg).forEachPixel( SetZero::setZero );
		//...and insert the marker
		le.isolateGivenLabel(inImg,inLabel, outImg,outputValue);
	}


	public static
	<IT extends RealType<IT>, ET extends RealType<ET>>
	void clearAndInsertBestWeightMarker(final Vector<RandomAccessibleInterval<IT>> inImgs,
	                                    final Vector<Float> inLabels,
	                                    final LabelExtractor<IT,?,ET> le,
	                                    final Vector<Double> inWeights,
	                                    final RandomAccessibleInterval<ET> outImg)
	{
		int bestWeightIdx = getBestWeightIndex(inImgs, inWeights);
		if (bestWeightIdx == -1)
		{
			System.out.print("failed: ");
			return;
		}
		else
		{
			System.out.print("from "+bestWeightIdx+": ");
		}

		clearAndInsertGivenMarker(inImgs.get(bestWeightIdx),inLabels.get(bestWeightIdx), le, inWeights.get(bestWeightIdx),outImg);
	}
}
