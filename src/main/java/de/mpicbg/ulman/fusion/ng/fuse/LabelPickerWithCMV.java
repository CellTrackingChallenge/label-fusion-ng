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
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.Intervals;
import org.scijava.log.Logger;

import java.util.Objects;
import java.util.Vector;

public class LabelPickerWithCMV<IT extends RealType<IT>, ET extends RealType<ET>>
implements LabelFuser<IT,ET>
{
	final long[] minBBox = new long[2];
	final long[] maxBBox = new long[2];

	private
	class CombinationData<LT extends IntegerType<LT>>
	{
		CombinationData(final Vector<RandomAccessibleInterval<IT>> inImgSlices,
		                final Vector<Float> inLabels,
		                final Interval fuseROIforSegGT,
		                final RandomAccessibleInterval<LT> segGtImg,
		                final int segGtLabel,
		                final Interval segBBoxInterval2D)
		{
			this.inImgSlices = inImgSlices;
			this.inLabels = inLabels;
			this.fuseROIforSegGT = fuseROIforSegGT;
			this.segGtImg = segGtImg;
			this.segGtLabel = segGtLabel;
			this.segBBoxInterval2D = segBBoxInterval2D;
		}

		final Vector<RandomAccessibleInterval<IT>> inImgSlices;
		final Vector<Float> inLabels;
		final Interval fuseROIforSegGT;
		final RandomAccessibleInterval<LT> segGtImg;
		final int segGtLabel;
		final Interval segBBoxInterval2D;
	}

	private
	//<LT extends IntegerType<LT>>
	double processOneCombination(final int c,
	                             final CombinationData<?> cd,
	                             final RandomAccessibleInterval<ET> fusedOutput)
	{
		int selectorBit = 0;
		int noOfActiveBits = 0;

		//iterate over inputs and append the active ones
		while ( (1 << selectorBit) <= c ) {
			if ( ((1 << selectorBit) & c) > 0 ) {
				++noOfActiveBits;
				if (noOfActiveBits == 1) {
					//first fuse input
					log.info("  in "+selectorBit+" (first fuser)");
				} else {
					//some next fuse input
					log.info("  in "+selectorBit+" (next fuser)");
				}
			}
			++selectorBit;
		}
		log.info("  total "+noOfActiveBits+" -> threshold "+(noOfActiveBits/2));

		return JaccardWithROIs.JaccardLB(
				fusedOutput, 1.0, cd.fuseROIforSegGT,
				cd.segGtImg, cd.segGtLabel, cd.segBBoxInterval2D
			);
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
					tmpInSlices.add( ld.slicedViewOf(inImgs.get(i)) );
					tmpInLabels.add( inLabels.get(i) );
				}
			final CombinationData<LT> cData = new CombinationData<>(
					tmpInSlices, tmpInLabels, fuseROIforSegGT,
					ld.lastLoadedImage,
					extractorForCherryPicker.traToSegLabelValues.get(extractorForCherryPicker.lastlyExtractedMarkerValue),
					segBBoxInterval2D );
			for (int combination = 1; combination < (1 << noOfAvailableInputs); combination++)
			{
				double score = processOneCombination(combination, cData, outImg);
				log.info("Combination "+combination+" yields SEG = "+score);
			}
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
