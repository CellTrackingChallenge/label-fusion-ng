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

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.loops.LoopBuilder;
import net.imglib2.type.numeric.RealType;
import java.util.Vector;
import de.mpicbg.ulman.fusion.ng.extract.LabelExtractor;
import net.celltrackingchallenge.measures.util.Jaccard;
import net.imglib2.type.operators.SetZero;

public class SIMPLELabelFuser<IT extends RealType<IT>, ET extends RealType<ET>>
implements LabelFuser<IT,ET>
{
	// explicit params of this particular fuser
	public int maxIters = 4;
	public int noOfNoUpdateIters = 2;
	public double initialQualityThreshold = 0.7;
	public double stepDownInQualityThreshold = 0.1;
	public double minimalQualityThreshold = 0.3;

	public
	String reportSettings()
	{
		return String.format("maxIters = %d, noOfNoUpdateIters = %d, initialQualityThreshold = %f, stepDownInQualityThreshold = %f, minimalQualityThreshold = %f",
			maxIters,noOfNoUpdateIters, initialQualityThreshold, stepDownInQualityThreshold, minimalQualityThreshold);
	}

	/**
	 * Values in the output image are binary: 0 - background, 1 - fused segment.
	 * Note that the output may not necessarily be a single connected component.
	 */
	@Override
	public
	void fuseMatchingLabels(final Vector<RandomAccessibleInterval<IT>> inImgs,
	                        final Vector<Float> inLabels,
	                        final LabelExtractor<IT,?,ET> le,
	                        final Vector<Double> inWeights,
	                        final Img<ET> outImg)
	{
		//da plan:
		// outImg will contain the current candidate fusion segment
		// one would always evaluate this segment against inImgs+inLabels pair,
		//   and adapt the inWeights accordingly
		// inputs that get below the quality threshold will be "erased" by
		//   setting their respective inImgs[i] to null

		//DEBUG
		System.out.print("it: 0 ");
		reportCurrentWeights(inImgs,inWeights);

		//make sure the majorityFuser is available
		if (majorityFuser == null) majorityFuser = new WeightedVotingLabelFuser<>();

		//initial candidate segment
		majorityFuser.minAcceptableWeight = getMajorityThreshold(inImgs,inWeights);
		majorityFuser.fuseMatchingLabels(inImgs,inLabels, le, inWeights,outImg);

		//own copy of the weights
		final Vector<Double> myWeights = new Vector<>(inWeights);
		double currentQualityThreshold = initialQualityThreshold;
		int iterationCnt = 1;

		while (iterationCnt < maxIters)
		{
			//update weights of the inputs that still pass the quality threshold
			for (int i=0; i < inImgs.size(); ++i)
			{
				//consider only available images
				if (inImgs.get(i) == null) continue;

				//adapt the weight
				final double newWeight = Jaccard.Jaccard(inImgs.get(i),inLabels.get(i), outImg,1.0);
				myWeights.set(i,newWeight);

				//filter out low-weighted ones (only after the initial settle-down phase)
				if (iterationCnt >= noOfNoUpdateIters && newWeight < currentQualityThreshold) inImgs.set(i,null);
			}

			//DEBUG
			System.out.print("it: "+iterationCnt+", thres: "+currentQualityThreshold+" ");
			reportCurrentWeights(inImgs,myWeights);

			//create a new candidate
			LoopBuilder.setImages(outImg).forEachPixel(SetZero::setZero);
			majorityFuser.minAcceptableWeight = getMajorityThreshold(inImgs,myWeights);
			majorityFuser.fuseMatchingLabels(inImgs,inLabels, le, myWeights,outImg);
			//TODO stopping flag when new outImg is different from the previous one

			//update the quality threshold
			++iterationCnt;
			if (iterationCnt > noOfNoUpdateIters) currentQualityThreshold = Math.max(
				currentQualityThreshold - stepDownInQualityThreshold*(iterationCnt-noOfNoUpdateIters),
				minimalQualityThreshold );
		}
	}

	private
	WeightedVotingLabelFuser<IT,ET> majorityFuser = null;

	/** Calculates a "0.5 threshold" given non-normalized weights w_i:
	    Given S = \Sum_i w_i -- a normalization yielding \Sum_i w_i/S = 1.0,
	    a pixel p is considered majority-voted iff
	    \Sum_i indicator_i(p) * w_i/S > 0.5, where indicator_i(p) \in {0,1}.
	    The returned threshold is the r.h.s. of the following equation
	    \Sum_i indicator_i(p) * w_i > 0.5*S. */
	private
	double getMajorityThreshold(final Vector<RandomAccessibleInterval<IT>> inImgs,
	                            final Vector<Double> inWeights)
	{
		double sum = 0.0;
		for (int i=0; i < inImgs.size(); ++i)
			if (inImgs.get(i) != null)
				sum += inWeights.get(i);

		return (0.5*sum + 0.0001);
		//NB: +0.0001 is here because WeightedVotingLabelFuser.fuseMatchingLabels()
		//evaluates >= 'threshold' (and not just > 'threshold')
	}


	private
	void reportCurrentWeights(final Vector<RandomAccessibleInterval<IT>> inImgs,
	                          final Vector<Double> inWeights)
	{
		System.out.print("weights: ");
		for (int i=0; i < inImgs.size(); ++i)
			System.out.printf("%+.3f\t",inImgs.get(i) != null ? inWeights.get(i).floatValue() : -1.f);
		System.out.println();
	}
}
