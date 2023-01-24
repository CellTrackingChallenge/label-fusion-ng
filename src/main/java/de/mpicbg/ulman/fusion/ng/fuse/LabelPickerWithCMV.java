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
package de.mpicbg.ulman.fusion.ng.fuse;

import de.mpicbg.ulman.fusion.ng.extract.LabelExtractor;
import de.mpicbg.ulman.fusion.ng.extract.LabelExtractorForCherryPicker;
import de.mpicbg.ulman.fusion.util.SegGtImageLoader;
import de.mpicbg.ulman.fusion.util.loggers.RestrictedConsoleLogger;
import de.mpicbg.ulman.fusion.util.JaccardWithROIs;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.loops.LoopBuilder;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.Intervals;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;
import org.scijava.log.Logger;

import java.util.Objects;
import java.util.Set;
import java.util.HashSet;
import java.util.Vector;

public class LabelPickerWithCMV<IT extends RealType<IT>, ET extends RealType<ET>>
implements LabelFuser<IT,ET>
{
	ET ONE = null;
	final long[] minBBox = new long[2];
	final long[] maxBBox = new long[2];

	private
	class CombinationData<LT extends IntegerType<LT>>
	{
		CombinationData(final Vector<RandomAccessibleInterval<IT>> inImgSlices,
		                final Vector<Float> inLabels,
		                final RandomAccessibleInterval<LT> segGtImg,
		                final int segGtLabel)
		{
			this.inImgSlices = inImgSlices;
			this.inLabels = inLabels;
			this.segGtImg = segGtImg;
			this.segGtLabel = segGtLabel;
		}

		final Vector<RandomAccessibleInterval<IT>> inImgSlices;
		final Vector<Float> inLabels;
		final RandomAccessibleInterval<LT> segGtImg;
		final int segGtLabel;
	}

	private
	double processOneCombination(final int c,
	                             final CombinationData<?> cd,
	                             final RandomAccessibleInterval<ET> fusedOutput)
	{
		if (ONE == null) {
			ONE = fusedOutput.getAt( fusedOutput.minAsPoint() ).createVariable();
			ONE.setOne();
		}

		int selectorBit = 0;
		int noOfActiveBits = 0;
		StringBuilder sb = new StringBuilder();

		//iterate over inputs and append the active ones
		while ( (1 << selectorBit) <= c ) {
			if ( ((1 << selectorBit) & c) > 0 ) {
				++noOfActiveBits;
				sb.append('Y');
				final float inPxVal = cd.inLabels.get(selectorBit);
				if (noOfActiveBits == 1) {
					//first fuse input
					LoopBuilder
							.setImages(cd.inImgSlices.get(selectorBit), fusedOutput)
							.forEachPixel( (i,o) -> { if (i.getRealFloat() == inPxVal) o.setOne(); else o.setZero(); } );
				} else {
					//some next fuse input
					LoopBuilder
							.setImages(cd.inImgSlices.get(selectorBit), fusedOutput)
							.forEachPixel( (i,o) -> { if (i.getRealFloat() == inPxVal) o.add(ONE); } );
				}
			}
			else sb.append('-');

			++selectorBit;
		}

		//finish the combination-signature with '-'
		while (selectorBit < cd.inLabels.size()) { sb.append('-'); ++selectorBit; }

		//voting here:
		noOfActiveBits /= 2;
		final float requiredVotingMinimum = noOfActiveBits+1;
		sb.append(" ; threshold >= ");
		sb.append(requiredVotingMinimum);
		LoopBuilder.setImages(fusedOutput).forEachPixel(
				o -> { if (o.getRealFloat() >= requiredVotingMinimum) o.setOne(); else o.setZero(); } );

		final double score = JaccardWithROIs.JaccardLB(
				fusedOutput, ONE.getRealFloat(), fusedOutput,
				cd.segGtImg, cd.segGtLabel, cd.segGtImg );

		log.info("SEG = "+ String.format("%.06f",score)
				+" <-- Combination " + String.format("%4d",c)
				+" : "+sb);
		return score;
	}

	<LT extends IntegerType<LT>>
	void fuseMatchingLabels_withExplicitLT(final Vector<RandomAccessibleInterval<IT>> inImgs,
	                                       final Vector<Float> inLabels,
	                                       final LabelExtractor<IT,LT,ET> le,
	                                       final Vector<Double> inWeights,
	                                       final RandomAccessibleInterval<ET> outImg,
	                                       final Interval fuseROI)
	{
		//the "secret channel" with the reliable access to the SEG content
		final LabelExtractorForCherryPicker<IT,LT,ET> extractorForCherryPicker = (LabelExtractorForCherryPicker<IT,LT,ET>)le;
		final int segLabel = extractorForCherryPicker.traToSegLabelValues.get(extractorForCherryPicker.lastlyExtractedMarkerValue);

		log.info("==============");
		log.info("tra marker: "+extractorForCherryPicker.lastlyExtractedMarkerValue);
		log.info("seg label : "+segLabel);
		log.info("seg image : "+extractorForCherryPicker.segGtImageLoader.getLastLoadedData().get(0).lastLoadedImageName);

		//copy of 'fuseROI' projected (from possibly 3D) to 2D,
		//this an interval, in essence, just around the union of prospective input labels
		final Interval fuseROIforSegGT = fuseROI.numDimensions() == 3 ? Intervals.hyperSlice(fuseROI,2) : fuseROI;

		for (SegGtImageLoader<LT>.LoadedData ld : extractorForCherryPicker.segGtImageLoader.getLastLoadedData())
		{
			//shortcut to the bbox of the currently examined SEG label, it may however happen that such
			//label is not in the currently processed SEG image... in which case we skip this SEG image
			final long[] segBBox = ld.calculatedBoxes.getOrDefault((double)segLabel,null);
			if (segBBox == null) continue;

			//setup a new (2D) ROI-interval just around the SEG label
			//NB: works well with ld.lastLoadedImage
			minBBox[0] = segBBox[0]; minBBox[1] = segBBox[1];
			maxBBox[0] = segBBox[2]; maxBBox[1] = segBBox[3];
			final Interval segBBoxInterval2D = new FinalInterval(minBBox,maxBBox);

			final int noOfAvailableInputs = (int)inImgs.stream().filter(Objects::nonNull).count();
			final Vector<RandomAccessibleInterval<IT>> tmpInSlices = new Vector<>(noOfAvailableInputs);
			final Vector<Float> tmpInLabels = new Vector<>(noOfAvailableInputs);
			for (int i = 0; i < inImgs.size(); ++i)
				if (inImgs.get(i) != null) {
					tmpInSlices.add( Views.interval( ld.slicedViewOf(inImgs.get(i)), fuseROIforSegGT ) );
					tmpInLabels.add( inLabels.get(i) );
				}
			final CombinationData<LT> cData = new CombinationData<>(
					tmpInSlices, tmpInLabels,
					Views.interval( ld.lastLoadedImage, segBBoxInterval2D ),
					extractorForCherryPicker.traToSegLabelValues.get(extractorForCherryPicker.lastlyExtractedMarkerValue) );

			final IntervalView<ET> outImgView = Views.interval( ld.slicedViewOf(outImg), fuseROIforSegGT );

			//iterate first over single inputs, then do groups of them
			//NB: this is only for log parsing purposes
			int bestCombination = -1;
			double bestScore = -1;
			//
			final Set<Integer> examinedCombinations = new HashSet<>(noOfAvailableInputs);
			for (int i = 0; i < noOfAvailableInputs; ++i) {
				int combination = 1 << i;
				examinedCombinations.add(combination);
				double score = processOneCombination(combination, cData, outImgView);
				if (score > bestScore) {
					bestScore = score;
					bestCombination = combination;
				}
			}
			//
			for (int combination = 3; combination < (1 << noOfAvailableInputs); combination++)
			{
				if (examinedCombinations.contains(combination)) continue;
				double score = processOneCombination(combination, cData, outImgView);
				if (score > bestScore) {
					bestScore = score;
					bestCombination = combination;
				}
			}

			log.info("Best combination "+bestCombination+" got SEG "+bestScore);
			processOneCombination(bestCombination, cData, outImgView);
		}
	}

	@Override
	public
	void fuseMatchingLabels(final Vector<RandomAccessibleInterval<IT>> inImgs,
	                        final Vector<Float> inLabels,
	                        final LabelExtractor<IT,?,ET> le,
	                        final Vector<Double> inWeights,
	                        final RandomAccessibleInterval<ET> outImg,
	                        final Interval fuseROI)
	{
		fuseMatchingLabels_withExplicitLT(inImgs,inLabels,le,inWeights,outImg,fuseROI);
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
	{
		this.log = log;
	}
}
