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
package de.mpicbg.ulman.fusion.ng.postprocess;

import net.imglib2.loops.LoopBuilder;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.Cursor;
import net.imglib2.Interval;
import net.imglib2.FinalInterval;
import net.imglib2.img.Img;
import net.imglib2.type.operators.SetZero;
import net.imglib2.view.Views;
import net.imglib2.view.IntervalView;
import net.imglib2.algorithm.labeling.ConnectedComponents;

import java.util.HashMap;
import de.mpicbg.ulman.fusion.ng.extract.MajorityOverlapBasedLabelExtractor;

import org.scijava.log.Logger;
import de.mpicbg.ulman.fusion.util.loggers.SimpleRestrictedLogger;

/**
 * @author Vladimír Ulman
 * @author Cem Emre Akbas
 */
public class KeepLargestCCALabelPostprocessor<LT extends IntegerType<LT>>
implements LabelPostprocessor<LT>
{
	@Override
	public
	void processLabel(final Img<LT> img,
	                  final int markerValue)
	{
		//localize the marker in the processed image
		final Cursor<LT> markerCursor = img.localizingCursor();
		while (markerCursor.hasNext() && markerCursor.next().getInteger() != markerValue) ;

		//determine marker's size and the AABB it spans
		final long[] minBound = new long[img.numDimensions()];
		final long[] maxBound = new long[img.numDimensions()];
		MajorityOverlapBasedLabelExtractor.findAABB(markerCursor, minBound,maxBound);
		final Interval ccaInterval = new FinalInterval(minBound, maxBound);

		processLabel(img,markerValue,ccaInterval);
	}


	//copy of just one label, and result of CCA on this one label
	private Img<LT> ccaInImg, ccaOutImg;

	@Override
	public
	void processLabel(final Img<LT> img,
	                  final int markerValue,
	                  final Interval ROI)
	{
		if (ccaInImg == null)
		{
			log.info("allocating CCA tmp images");
			ccaInImg = img.factory().create(img);
			ccaOutImg = img.factory().create(img);
			log.info("allocating done");
		}

		final IntervalView<LT> imgView = Views.interval(img,ROI);
		final IntervalView<LT> ccaInView = Views.interval(ccaInImg,ROI);
		final IntervalView<LT> ccaOutView = Views.interval(ccaOutImg,ROI);

		//copy out only the currently examined marker
		LoopBuilder.setImages(imgView,ccaInView)
				.forEachPixel( (s,t) -> t.setInteger(s.getInteger() == markerValue ? 1 : 0) );

		//since the View comes from one shared large image, there might be results of CCA for other markers,
		//we better clear it before (so that the CCA function cannot be fooled by some previous result)
		LoopBuilder.setImages(ccaOutView).forEachPixel(SetZero::setZero);

		//CCA to this View
		final int noOfLabels
			= ConnectedComponents.labelAllConnectedComponents(ccaInView,ccaOutView,
				ConnectedComponents.StructuringElement.EIGHT_CONNECTED);

		//is there anything to change?
		if (noOfLabels > 1)
		{
			log.info("CCA for marker "+markerValue+": choosing one from "+noOfLabels+" components");

			//calculate sizes of the detected labels
			final HashMap<Integer,Integer> hist = new HashMap<>(10);
			final Cursor<LT> ccaOutCursor = ccaOutView.cursor();
			while (ccaOutCursor.hasNext())
			{
				final int curLabel = ccaOutCursor.next().getInteger();
				if (curLabel == 0) continue; //skip over the background component
				hist.put(curLabel, 1 + hist.getOrDefault(curLabel,0) );
			}

			//find the most frequent pixel value (the largest label)
			int largestCC = -1;
			int largestSize = 0;
			int totalSize = 0;
			for (int lab : hist.keySet())
			{
				final int size = hist.get(lab);
				if (size > largestSize)
				{
					largestSize = size;
					largestCC   = lab;
				}
				totalSize += size;
			}
			log.info("CCA for marker "+markerValue+": chosen component no. "+largestCC+" which constitutes "
									 +(float)largestSize/(float)totalSize+" % of the original size");

			//remove anything from the current marker that does not overlap with the largest CCA component
			final Cursor<LT> imgCursor = imgView.cursor();
			ccaOutCursor.reset();
			while (ccaOutCursor.hasNext())
			{
				//'totalSize' is borrowed to be a component value/id in the CCA image
				totalSize = ccaOutCursor.next().getInteger();
				if ( imgCursor.next().getInteger() == markerValue && totalSize != largestCC )
					imgCursor.get().setZero();
			}
		}
	}

	// ---------------- logging ----------------
	Logger log = new SimpleRestrictedLogger();
	@Override
	public void useNowThisLog(final Logger log)
	{ this.log = log; }
}
