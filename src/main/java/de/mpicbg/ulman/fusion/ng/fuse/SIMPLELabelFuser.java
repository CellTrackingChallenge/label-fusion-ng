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

import de.mpicbg.ulman.fusion.util.JaccardWithROIs;
import de.mpicbg.ulman.fusion.util.SegGtImageLoader;
import de.mpicbg.ulman.fusion.util.loggers.RestrictedConsoleLogger;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.loops.LoopBuilder;
import net.imglib2.type.numeric.RealType;

import java.util.List;
import java.util.LinkedList;
import java.util.Vector;
import de.mpicbg.ulman.fusion.ng.extract.LabelExtractor;
import net.celltrackingchallenge.measures.util.Jaccard;
import net.imglib2.type.operators.SetZero;
import org.scijava.log.Logger;
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
		return String.format("maxIters = %d, noOfNoPruneIters = %d, initialQualityThreshold = %.2f, stepDownInQualityThreshold = %.2f, minimalQualityThreshold = %.2f",
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
	                        final RandomAccessibleInterval<ET> outImg,
	                        final Interval fuseROI)
	{
		//da plan:
		// outImg will contain the current candidate fusion segment
		// one would always evaluate this segment against inImgs+inLabels pair,
		//   and adapt the inWeights accordingly
		// inputs that get below the quality threshold will be "erased" by
		//   setting their respective inImgs[i] to null

		//TODO DEBUG (block starts for gnuplot)
		log.debug("\n\n");
		//DEBUG -- report-only Oracle weights (something we normally don't have at hand)
		log.debug("it: -1.5 0.0 " + reportCurrentWeights(inImgs,inWeights));

		//prepare flat local weights
		final Vector<Double> myWeights = new Vector<>(inWeights);
		myWeights.replaceAll(ignored -> 1.0);

		//report the flat weights, just to be on the safe side
		log.debug("it: 0.0 0.0 " + reportCurrentWeights(inImgs,inWeights));

		//make sure the majorityFuser is available
		if (majorityFuser == null) majorityFuser = new WeightedVotingLabelFuser<>();

		//initial candidate segment
		final double auxFusedLabel = WeightedVotingLabelFuser.FUSION_LABEL;
		majorityFuser.minAcceptableWeight = getMajorityThreshold(inImgs,myWeights); //majority?? or, 1/3??
		majorityFuser.fuseMatchingLabels(inImgs,inLabels, le, myWeights,outImg);
		log.trace("#it: 0, voting thres: "+majorityFuser.minAcceptableWeight);

		/*
		//DEBUG
		if (inLabels.get(1) == 7290)
			SimplifiedIO.saveImage(outImg, "/temp/CE_02/tmp/SIMPLEcase1_1_initialSegment.tif");
		*/

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
				final double newWeight = Jaccard.Jaccard(inImgs.get(i),inLabels.get(i), outImg,auxFusedLabel);
				myWeights.set(i,newWeight);
			}

			//DEBUG: report updated weights based on the current candidate
			double jaccard = 0;
			if (segGTlabel > 0) { //shall we do any debug Jaccarding?
				log.trace("Doing debug Jaccards for SEG label "+segGTlabel);
				jaccard = getJaccard(outImg, auxFusedLabel, fuseROI);
			}
			log.debug("it: "+(iterationCnt-0.1)+" "
					+ jaccard+" "
					+ reportCurrentWeights(inImgs,myWeights));
			log.trace("#it: "+iterationCnt+", prunning thres: "+currentQualityThreshold);

			//prune poor inputs
			for (int i=0; i < inImgs.size(); ++i)
			{
				//consider only available images
				if (inImgs.get(i) == null) continue;

				//filter out low-weighted ones (only after the initial settle-down phase)
				if (iterationCnt >= noOfNoPruneIters && myWeights.get(i) < currentQualityThreshold) inImgs.set(i,null);
			}

			//DEBUG: report how the prunning ended up
			log.debug("it: "+(iterationCnt+0.0)+" "
					+ jaccard+" "
					+ reportCurrentWeights(inImgs,myWeights));

			//create a new candidate
			LoopBuilder.setImages(outImg).forEachPixel(SetZero::setZero);
			majorityFuser.minAcceptableWeight = getMajorityThreshold(inImgs,myWeights);
			majorityFuser.fuseMatchingLabels(inImgs,inLabels, le, myWeights,outImg);
			log.trace("#it: "+iterationCnt+", voting thres: "+majorityFuser.minAcceptableWeight);
			//TODO stopping flag when new outImg is different from the previous one

			/*
			//DEBUG
			if (inLabels.get(1) == 7290)
				SimplifiedIO.saveImage(outImg, "/temp/CE_02/tmp/SIMPLEcase"+dbgImageCounter+"_"+(iterationCnt+1)+"_candidateSegment.tif");
			*/

			//update the quality threshold
			++iterationCnt;
			if (iterationCnt > noOfNoPruneIters) currentQualityThreshold = Math.min(
				initialQualityThreshold + stepDownInQualityThreshold * (iterationCnt-noOfNoPruneIters),
				minimalQualityThreshold );
		}

		//compute Jaccard for the final candidate segment
		//LoopBuilder.setImages(outImg).forEachPixel( (a) -> { if (a.getRealFloat() > 0) a.setOne(); else a.setZero(); } );
		double jaccard = 0;
		if (segGTlabel > 0) { //shall we do any debug Jaccarding?
			jaccard = getJaccard(outImg, auxFusedLabel, fuseROI);
		}
		log.debug("it: "+(iterationCnt-0.3)+" "
				+ jaccard+" "
				+ reportCurrentWeights(inImgs,myWeights));
		//TODO log.debug("# GT_label="+GT_currentLabel+" SEG "+jaccard);

		//DEBUG (will appear just before "TRA marker: .....")
		/*
		System.out.println("# PLACES: scores estimated from this code");
		reportInOrder(inImgs,myWeights, myPlaces);
		System.out.println("# (reported with counter "+(dbgImageCounter++)+")");
		System.out.print("# ");
		*/
	}

	private
	WeightedVotingLabelFuser<IT,ET> majorityFuser = null;

	// =================== GT debug ===================
	SegGtImageLoader<?> segGT = null;
	int segGTlabel = 0;
	final long[] min = new long[2];
	final long[] max = new long[2];

	private double getJaccard(final RandomAccessibleInterval<ET> outImg,
	                          final double outImgLabel,
	                          final Interval fuseROI )
	{
		//go over all GT available and try to find the one including the current SEG GT label
		for (SegGtImageLoader<?>.LoadedData ld : segGT.getLastLoadedData()) {
			if (ld.calculatedBoxes.containsKey((double)segGTlabel)) {
				//...found the right SEG GT record
				final long[] gtBBox = ld.calculatedBoxes.get((double)segGTlabel);
				min[0] = gtBBox[0]; min[1] = gtBBox[1];
				max[0] = gtBBox[2]; max[1] = gtBBox[3];
				return JaccardWithROIs.JaccardLB(
						ld.slicedViewOf(outImg), outImgLabel,
						fuseROI,
						ld.lastLoadedImage, segGTlabel,
						new FinalInterval(min,max)
				);
			}
		}
		return 0;
	}
	// =================== GT debug ===================

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
	String reportCurrentWeights(final Vector<RandomAccessibleInterval<IT>> inImgs,
	                          final Vector<Double> inWeights)
	{
		final StringBuilder sb = new StringBuilder("weights: ");
		for (int i=0; i < inImgs.size(); ++i)
			sb.append(String.format("%+.3f\t",inImgs.get(i) != null ? inWeights.get(i).floatValue() : -0.2f));
			//NB: -0.2 is to indicate we dropped it (Jaccard cannot get below 0.0)
		return sb.toString();
	}


	private
	void reportInOrder(final Vector<RandomAccessibleInterval<IT>> inImgs,
	                   final Vector<Double> inWeights,
	                   final Vector< List<Integer> > places)
	{
		//over all inputs, find for each how many are smaller than the current one
		for (int i=0; i < inImgs.size(); ++i)
		{
			if (inImgs.get(i) == null)
			{
				//add "last place" to all inputs that are no longer valid by now
				places.get(i).add(inImgs.size());
				continue;
			}

			int noOfOthersThatAreBetter = 0;
			for (int j=0; j < inImgs.size(); ++j)
			{
				if (inImgs.get(j) == null) continue; //skip invalid
				if (j == i) continue;                //skip myself
				if (inWeights.get(j) >= inWeights.get(i)) ++noOfOthersThatAreBetter;
			}

			places.get(i).add(noOfOthersThatAreBetter+1);
			//NB: "+1" to become 1-based position
		}

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


	public
	void reportInputsSorting(int timepoint)
	{
		Vector<Float> avgs = new Vector<>( truePlaces.size() );

		boolean foundEmpty = false;
		int curInput = 0;

		//report position-graph for Jaccard
		while (curInput < truePlaces.size() && !foundEmpty)
		{
			float avgPos = 0;
			for (int p : truePlaces.get(curInput)) avgPos += (float)p;

			if (truePlaces.get(curInput).size() > 0)
			{
				avgPos /= (float)truePlaces.get(curInput).size();
				avgs.add(avgPos);
				++curInput;
			}
			else
				foundEmpty = true;
		}

		for (int i=0; i < curInput; ++i)
			System.out.println("# TP "+timepoint+" Jaccard: inputNo. averagePos. "+(i+1)+" "+avgs.get(i));
		System.out.println("# TP "+timepoint+" Jaccard: inputNo. averagePos. SEPARATOR line");
		System.out.println("# TP "+timepoint+" Jaccard: inputNo. averagePos. SEPARATOR line");

		foundEmpty = false;
		curInput = 0;

		//report position-graph from our prediction
		while (curInput < myPlaces.size() && !foundEmpty)
		{
			float avgPos = 0;
			for (int p : myPlaces.get(curInput)) avgPos += (float)p;

			if (myPlaces.get(curInput).size() > 0)
			{
				avgPos /= (float)myPlaces.get(curInput).size();
				avgs.set(curInput,avgPos);
				++curInput;
			}
			else
				foundEmpty = true;
		}

		for (int i=0; i < curInput; ++i)
			System.out.println("# TP "+timepoint+" Prediction: inputNo. averagePos. "+(i+1)+" "+avgs.get(i));

		//prepare groups: best, best two, best three, etc...
		//sort first
		class InputScore {
			InputScore(float a, int i) { avgPos = a; inputId =i ; }
			float avgPos;
			int inputId;
		}
		List< InputScore > ranked = new LinkedList<>();
		for (int i=0; i < curInput; ++i)
			ranked.add( new InputScore(avgs.get(i),i) );
		ranked.sort( (o1,o2) -> {
			if (o1.avgPos < o2.avgPos) return -1;
			return 1;
		} );

		for (int i=0; i < curInput; ++i)
		{
			int binaryMask = 0;
			for (int j=0; j <= i; ++j)
				binaryMask |= 1 << ranked.get(j).inputId;

			//System.out.print("# TP "+timepoint+" GROUP "+binaryMask+" consists of inputs: ");
			System.out.printf("# TP %d GROUP %08d consists of inputs: ",timepoint,binaryMask);
			for (int j=0; j <= i; ++j)
				System.out.print((ranked.get(j).inputId+1)+" ("+ranked.get(j).avgPos+") ");
			System.out.println();
		}


		//prepare for another image
		resetPlaces(myPlaces);
		resetPlaces(truePlaces);
	}

	@Override
	public
	void fuseMatchingLabels(final Vector<RandomAccessibleInterval<IT>> inImgs,
	                        final Vector<Float> inLabels,
	                        final LabelExtractor<IT,?,ET> le,
	                        final Vector<Double> inWeights,
	                        final RandomAccessibleInterval<ET> outImg)
	{
		log.error("unimplemented fusion regime");
	}

	// ---------------- logging ----------------
	Logger log = new RestrictedConsoleLogger();
	@Override
	public void useNowThisLog(final Logger log)
	{ this.log = log; }
}