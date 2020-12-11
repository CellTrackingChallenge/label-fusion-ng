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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import de.mpicbg.ulman.fusion.ng.extract.LabelExtractor;
import net.celltrackingchallenge.measures.util.Jaccard;
import net.imglib2.type.operators.SetZero;
import net.imglib2.view.Views;
import sc.fiji.simplifiedio.SimplifiedIO;

public class SIMPLELabelFuser<IT extends RealType<IT>, ET extends RealType<ET>>
implements LabelFuser<IT,ET>
{
	// explicit params of this particular fuser
	public int maxIters = 6;
	public int noOfNoPruneIters = 2;
	public double initialQualityThreshold = 0.5;
	public double stepDownInQualityThreshold = 0.1;
	public double minimalQualityThreshold = 0.7;

	public
	String reportSettings()
	{
		return String.format("maxIters = %d, noOfNoPruneIters = %d, initialQualityThreshold = %f, stepDownInQualityThreshold = %f, minimalQualityThreshold = %f",
			maxIters, noOfNoPruneIters, initialQualityThreshold, stepDownInQualityThreshold, minimalQualityThreshold);
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

		//DEBUG (block starts for gnuplot)
		System.out.println();
		System.out.println();

		//DEBUG -- report-only Oracle weights (something we normally don't have at hand)
		System.out.print("it: -2.5 0.0 ");
		reportCurrentWeights(inImgs,inWeights);

		//Jaccards of the inputs for this particular marker
		//prepare flat local weights
		final Vector<Double> myWeights = new Vector<>(inWeights);
		for (int i=0; i < inImgs.size(); ++i)
		{
			if (inImgs.get(i) == null) continue;
			myWeights.set(i, Jaccard.Jaccard(Views.hyperSlice(inImgs.get(i),2,19),inLabels.get(i), GT_segImage,GT_currentLabel) );
		}

		//DEBUG -- report-only our estimated weights
		System.out.print("it: -1.5 0.0 ");
		reportCurrentWeights(inImgs,myWeights);

		System.out.println("# PLACES: scores from Jaccard");
		reportInOrder(inImgs,myWeights, truePlaces);

		//prepare flat local weights
		for (int i=0; i < myWeights.size(); ++i) myWeights.set(i, 1.0);

		//report the flat weights, just to be on the safe side
		System.out.print("it: 0.0 0.0 ");
		reportCurrentWeights(inImgs,myWeights);

		//make sure the majorityFuser is available
		if (majorityFuser == null) majorityFuser = new WeightedVotingLabelFuser<>();

		//initial candidate segment
		majorityFuser.minAcceptableWeight = getMajorityThreshold(inImgs,myWeights); //majority?? or, 1/3??
		majorityFuser.fuseMatchingLabels(inImgs,inLabels, le, myWeights,outImg);

		//DEBUG: report...
		System.out.println("#it: 0, voting thres: "+majorityFuser.minAcceptableWeight);

		//DEBUG
		if (inLabels.get(1) == 7290)
			SimplifiedIO.saveImage(outImg, "/temp/CE_02/tmp/SIMPLEcase"+dbgImageCounter+"_1_initialSegment.tif");

		double currentQualityThreshold = initialQualityThreshold;
		int iterationCnt = 1; //how many times a candidate was created

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
			}

			//DEBUG: report updated weights based on the current candidate
			double jaccard = Jaccard.Jaccard(Views.hyperSlice(outImg,2,19),1, GT_segImage,GT_currentLabel);
			System.out.print("it: "+(iterationCnt-0.1)+" "+jaccard+" ");
			reportCurrentWeights(inImgs,myWeights);
			System.out.println("#it: "+iterationCnt+", prunning thres: "+currentQualityThreshold);

			//prune poor inputs
			for (int i=0; i < inImgs.size(); ++i)
			{
				//consider only available images
				if (inImgs.get(i) == null) continue;

				//filter out low-weighted ones (only after the initial settle-down phase)
				if (iterationCnt >= noOfNoPruneIters && myWeights.get(i) < currentQualityThreshold) inImgs.set(i,null);
			}

			//DEBUG: report how the prunning ended up
			System.out.print("it: "+(iterationCnt+0.0)+" ");
			System.out.print(jaccard+" ");
			reportCurrentWeights(inImgs,myWeights);

			//create a new candidate
			LoopBuilder.setImages(outImg).forEachPixel(SetZero::setZero);
			majorityFuser.minAcceptableWeight = getMajorityThreshold(inImgs,myWeights);
			majorityFuser.fuseMatchingLabels(inImgs,inLabels, le, myWeights,outImg);
			//TODO stopping flag when new outImg is different from the previous one

			//DEBUG
			if (inLabels.get(1) == 7290)
				SimplifiedIO.saveImage(outImg, "/temp/CE_02/tmp/SIMPLEcase"+dbgImageCounter+"_"+(iterationCnt+1)+"_candidateSegment.tif");
			//DEBUG: report...
			System.out.println("#it: "+iterationCnt+", voting thres: "+majorityFuser.minAcceptableWeight);

			//update the quality threshold
			++iterationCnt;
			if (iterationCnt > noOfNoPruneIters) currentQualityThreshold = Math.min(
				initialQualityThreshold + stepDownInQualityThreshold * (iterationCnt-noOfNoPruneIters),
				minimalQualityThreshold );
		}

		//compute Jaccard for the final candidate segment
		//LoopBuilder.setImages(outImg).forEachPixel( (a) -> { if (a.getRealFloat() > 0) a.setOne(); else a.setZero(); } );
		double jaccard = Jaccard.Jaccard(Views.hyperSlice(outImg,2,19),1, GT_segImage,GT_currentLabel);
		System.out.print("it: "+(iterationCnt-0.3)+" ");
		System.out.print(jaccard+" ");
		reportCurrentWeights(inImgs,myWeights);
		System.out.println("# GT_label="+GT_currentLabel+" SEG "+jaccard);

		//DEBUG (will appear just before "TRA marker: .....")
		System.out.println("# PLACES: scores estimated from this code");
		reportInOrder(inImgs,myWeights, myPlaces);
		System.out.println("# (reported with counter "+(dbgImageCounter++)+")");
		System.out.print("# ");
	}

	private
	int dbgImageCounter = 1;

	public int GT_currentLabel = -1;
	public RandomAccessibleInterval<IT> GT_segImage;

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
			System.out.printf("%+.3f\t",inImgs.get(i) != null ? inWeights.get(i).floatValue() : -0.2f);
			//NB: -0.2 is to indicate we dropped it (Jaccard cannot get below 0.0)
		System.out.println();
	}


	private
	void reportInOrder(final Vector<RandomAccessibleInterval<IT>> inImgs,
	                   final Vector<Double> inWeights,
	                   final Vector< List<Integer> > places)
	{
		double currentMax  = 1.5;
		for (int i=0; i < inImgs.size(); ++i)
		{
			//if (inImgs.get(i) == null) continue;
			double currentBest = -0.2;
			int currentBestInput = 99;

			for (int j=0; j < inImgs.size(); ++j)
			{
				if (inImgs.get(j) == null) continue;
				double w = inWeights.get(j);
				if (w > currentBest && w < currentMax)
				{
					currentBest = w;
					currentBestInput = j;
				}
			}

			//System.out.printf("# ORDER: %d ( %+.3f )\n",currentBestInput,currentBest);
			currentMax = currentBest;

			if (currentBestInput < 99) places.get(currentBestInput).add(i+1);
		}
		//System.out.println("# ORDER: ");

		//print the current places:
		for (int i=0; i < inImgs.size(); ++i)
		{
			System.out.print("# PLACES "+(i+1)+" : ");
			//System.out.print("# INPUT "+(i+1)+" : ");
			for (int pos : places.get(i))
				System.out.print(pos+" ");
				//System.out.print((i+1)+" "+pos+";");
			System.out.println();
		}
	}

	//places[inputNo].add[currentOrder]
	Vector< List<Integer> > myPlaces = new Vector<>(16);
	Vector< List<Integer> > truePlaces = new Vector<>(16);
	{
		resetPlaces( myPlaces);
		resetPlaces( truePlaces );
	}

	private
	void resetPlaces(final Vector< List<Integer> > places)
	{
		places.clear();
		while (places.size() < places.capacity())
			places.add( new Vector<>(20) );
	}
}
