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
package de.mpicbg.ulman.fusion.ng;

import de.mpicbg.ulman.fusion.ng.extract.LabelExtractor;
import de.mpicbg.ulman.fusion.ng.extract.MajorityOverlapBasedLabelExtractor;
import de.mpicbg.ulman.fusion.ng.fuse.LabelPicker;
import de.mpicbg.ulman.fusion.ng.insert.CollisionsManagingLabelInsertor;
import de.mpicbg.ulman.fusion.ng.postprocess.KeepLargestCCALabelPostprocessor;
import de.mpicbg.ulman.fusion.util.SegGtImageLoader;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.ByteType;
import net.imglib2.view.Views;
import org.scijava.log.Logger;
import java.util.Map;
import java.util.Vector;

public
class CherryPicker<IT extends RealType<IT>, LT extends IntegerType<LT>>
			extends AbstractWeightedVotingRoisFusionAlgorithm<IT,LT, ByteType>
{
	//IT: Input type = participant's segmentation results
	//LT: Marker file type = man_trackTTT.tif
	//ByteType: is the type of the helping aux image
	public CherryPicker(Logger _log, SegGtImageLoader<LT> _segImgLoader) {
		super(_log, new ByteType());
		segGtImageLoader = _segImgLoader;
		segGtImageExtractor = new MajorityOverlapBasedLabelExtractor<>();
	}

	final SegGtImageLoader<LT> segGtImageLoader;
	//
	final LabelExtractor<LT,LT,ByteType> segGtImageExtractor;
	final long[] minBBox = new long[2];
	final long[] maxBBox = new long[2];

	@Override
	protected void setFusionComponents() {
		//setup the individual stages
		final MajorityOverlapBasedLabelExtractor<IT,LT, ByteType> e = new MajorityOverlapBasedLabelExtractor<>();
		e.minFractionOfMarker = 0.5f;

		final LabelPicker<IT,ByteType> f = new LabelPicker<>();

		final CollisionsManagingLabelInsertor<LT, ByteType> i = new CollisionsManagingLabelInsertor<>();
		final KeepLargestCCALabelPostprocessor<LT> p = new KeepLargestCCALabelPostprocessor<>();

		this.labelExtractor = e;
		this.labelFuser     = f;
		this.labelInsertor  = i;
		this.labelCleaner   = p;
	}

	@Override
	public Img<LT> fuse(final Vector<RandomAccessibleInterval<IT>> inImgs,
	                    final Img<LT> markerImg)
	{
		log.info("CherryPicker's outer fuse() is narrowing TRA markers to SEG segments only");

		//first add all markers on the ignore list....
		for (double marker : markerBoxes.keySet()) ignoredMarkersTemporarily.add((int)marker);

		//....then remove the ones matching SEG....
		for (SegGtImageLoader<LT>.LoadedData ld : segGtImageLoader.getLastLoadedData()) {
			//....by considering all loaded SEG images for this timepoint....
			final RandomAccessibleInterval<LT> markerSliceImg = ld.slicedViewOf(markerImg);
			log.info("  dimension of markerImg: "+markerImg.numDimensions()+" (should be 2 or 3)");
			log.info("  dimension of markerSliceImg: "+markerSliceImg.numDimensions()+" (should be 2)");

			//....against the all markers
			for (Map.Entry<Double,long[]> marker : markerBoxes.entrySet()) {
				final long[] curBBox = marker.getValue();
				final int idxCompensationFor2d = ld.lastLoadedIs2D ? 0 : 1;
				minBBox[0] = curBBox[0];
				minBBox[1] = curBBox[1];
				maxBBox[0] = curBBox[3-idxCompensationFor2d];
				maxBBox[1] = curBBox[4-idxCompensationFor2d];
				final Interval mInterval = new FinalInterval(minBBox, maxBBox);

				//check if there is a SEG segment overlapping with this box
				final int curMarker = marker.getKey().intValue();
				final float segLabel = segGtImageExtractor.findMatchingLabel(
						Views.interval(ld.lastLoadedImage, mInterval),
						Views.interval(markerSliceImg,     mInterval),
						curMarker);

				if (segLabel > 0) {
					log.info("  marker "+curMarker+" coincides with SEG label "+segLabel);
					ignoredMarkersTemporarily.remove(curMarker);
				} else {
					log.info("  marker "+curMarker+" is not matched in SEG");
				}
			}
		}
		log.info("CherryPicker's outer fuse() wants to skip markers: "+ignoredMarkersTemporarily);

		return super.fuse(inImgs, markerImg);
	}
}