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
package de.mpicbg.ulman.fusion.ng.postprocess;

import net.imglib2.type.numeric.IntegerType;
import net.imglib2.Cursor;
import net.imglib2.Interval;
import net.imglib2.FinalInterval;
import net.imglib2.img.Img;
import net.imglib2.view.Views;
import net.imglib2.view.IntervalView;
import net.imglib2.algorithm.labeling.ConnectedComponents;

import java.util.HashMap;
import de.mpicbg.ulman.fusion.ng.extract.MajorityOverlapBasedLabelExtractor;

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


	@Override
	public
	void processLabel(final Img<LT> img,
	                  final int markerValue,
	                  final Interval ROI)
	{
		//copy of just one label
		final Img<LT> ccaInImg = img.factory().create(img);

		//result of CCA on this one label
		final Img<LT> ccaOutImg = img.factory().create(img);

		//copy out only this marker
		final IntervalView<LT> ccaInView = Views.interval(ccaInImg,ROI);
		      Cursor<LT> ccaCursor = ccaInView.cursor();
		final Cursor<LT> outCursor = Views.interval(img,ROI).cursor();
		while (ccaCursor.hasNext())
		{
			ccaCursor.next().setInteger( outCursor.next().getInteger() == markerValue ? 1 : 0 );
		}

		//CCA to this View
		final IntervalView<LT> ccaOutView = Views.interval(ccaOutImg,ROI);
		//since the View comes from one shared large image, there might be results of CCA for other markers,
		//we better clear it before (so that the CCA function cannot be fooled by some previous result)
		ccaCursor = ccaOutView.cursor();
		while (ccaCursor.hasNext()) ccaCursor.next().setZero();

		final int noOfLabels
			= ConnectedComponents.labelAllConnectedComponents(ccaInView,ccaOutView, ConnectedComponents.StructuringElement.EIGHT_CONNECTED);

		//is there anything to change?
		if (noOfLabels > 1)
		{
			System.out.println("CCA for marker "+markerValue+": choosing one from "+noOfLabels+" components");

			//calculate sizes of the detected labels
			final HashMap<Integer,Integer> hist = new HashMap<>(10);
			ccaCursor.reset();
			while (ccaCursor.hasNext())
			{
				final int curLabel = ccaCursor.next().getInteger();
				if (curLabel == 0) continue; //skip over the background component
				final Integer count = hist.get(curLabel);
				hist.put(curLabel, count == null ? 1 : count+1 );
			}

			//find the most frequent pixel value (the largest label)
			int largestCC = -1;
			int largestSize = 0;
			int totalSize = 0;
			for (Integer lab : hist.keySet())
			{
				final int size = hist.get(lab);
				if (size > largestSize)
				{
					largestSize = size;
					largestCC   = lab;
				}
				totalSize += size;
			}
			System.out.println("CCA for marker "+markerValue+": chosen component no. "+largestCC+" which constitutes "
									 +(float)largestSize/(float)totalSize+" % of the original size");

			//remove anything from the current marker that does not overlap with the largest CCA component
			ccaCursor.reset();
			outCursor.reset();
			while (ccaCursor.hasNext())
			{
				ccaCursor.next();
				if ( outCursor.next().getInteger() == markerValue && ccaCursor.get().getInteger() != largestCC )
					outCursor.get().setZero();
			}
		}
	}
}
