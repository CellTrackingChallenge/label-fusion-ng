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
import net.celltrackingchallenge.measures.util.Jaccard;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.ByteType;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;
import org.scijava.log.Logger;
import java.util.Vector;

public class LabelPicker<IT extends RealType<IT>, ET extends RealType<ET>>
implements LabelFuser<IT,ET>
{
	final ByteType ONE = new ByteType((byte)1);
	final long[] minBBox = new long[2];
	final long[] maxBBox = new long[2];

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

		//for every 'i':
		//   inLabels[i] from inImgs[i]
		//   (may consider inWeights[i])
		//   score (Jaccard) inLabels[i] against current_SEG
		//   output the highest 'i' into outImg
		//   (based on the context: there shall be always at one such)
		//
		//where current_SEG is taken from the "secret channel" -> reachable from 'le'
		final Vector<Double> scores = new Vector<>(inLabels.size());
		for (SegGtImageLoader<LT>.LoadedData ld : extractorForCherryPicker.segGtImageLoader.getLastLoadedData())
		{
			//shortcut to the bbox of the currently examined SEG label, it may however happen that such
			//label is not in the currently processed SEG image... in which case we skip this SEG image
			final long[] segBBox = ld.calculatedBoxes.getOrDefault((double)segLabel,null);
			if (segBBox == null) continue;

			//setup a new (2D) ROI-interval as the union of the fuseROI and SEG label ROI
			minBBox[0] = Math.min( segBBox[0], fuseROI.min(0) );
			minBBox[1] = Math.min( segBBox[1], fuseROI.min(1) );
			maxBBox[0] = Math.max( segBBox[2], fuseROI.max(0) );
			maxBBox[1] = Math.max( segBBox[3], fuseROI.max(1) );
			final Interval roi = new FinalInterval(minBBox,maxBBox);
			final IntervalView<LT> viewAroundSEG = Views.interval(ld.lastLoadedImage, roi);

			for (int i = 0; i < inImgs.size(); ++i)
				if (inImgs.get(i) != null)
				{
					final RandomAccessibleInterval<IT> inImgSlice = ld.slicedViewOf(inImgs.get(i));
					scores.add( Jaccard.Jaccard(
							Views.interval(inImgSlice, roi),
							inLabels.get(i),
							viewAroundSEG,
							extractorForCherryPicker.traToSegLabelValues.get(extractorForCherryPicker.lastlyExtractedMarkerValue)
					) );
				}
				else scores.add( -2.0 );
		}
		if (scores.size() == 0) {
			log.error("What!? SEG label "+segLabel+" wasn't found in any of the SEG images associated to this timepoint, cannot cherry pick, skipping...");
			return;
		}

		log.info("--------------");
		double bestScore = -1;
		int bestScoreIdx = -1;
		for (int i = 0; i < inImgs.size(); ++i) {
			if (inImgs.get(i) != null) {
				log.info("Considering "+i+"th input with label "+inLabels.get(i)
						+": scores "+scores.get(i)+", weights "+inWeights.get(i));
			}

			if (scores.get(i) > bestScore) {
				bestScore = scores.get(i);
				bestScoreIdx = i;
			}
		}

		//take the best...
		if (bestScoreIdx > -1) {
			log.info("Inserting idx = "+bestScoreIdx+" (because of its score "+bestScore+")");
			le.isolateGivenLabel(
					Views.interval(inImgs.get(bestScoreIdx),fuseROI),inLabels.get(bestScoreIdx),
					Views.interval(outImg,fuseROI), (ET)ONE
			);
		} else {
			log.error("Hmmm... there should always be at least one image that also matches this SEG label.");
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
	{ this.log = log; }
}