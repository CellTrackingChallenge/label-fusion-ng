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
package de.mpicbg.ulman.fusion.ng.extract;

import net.imglib2.Cursor;
import net.imglib2.IterableInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.loops.LoopBuilder;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.IntegerType;

import java.util.HashMap;
import java.util.Iterator;

import org.scijava.log.Logger;
import de.mpicbg.ulman.fusion.util.loggers.SimpleRestrictedLogger;

public class MajorityOverlapBasedLabelExtractor<IT extends RealType<IT>, LT extends IntegerType<LT>, ET extends RealType<ET>>
implements LabelExtractor<IT,LT,ET>
{
	/** convenience (alias) method to set the this.minFractionOfMarker attribute */
	public
	void setMinOverlapRatio(final float minFractionOfMarker)
	{
		this.minFractionOfMarker = minFractionOfMarker;
	}

	public
	float minFractionOfMarker = 0.5f;

	/**
	 * Sweeps over 'markerValue' labelled voxels inside the marker image
	 * 'markerII', checks labels found in the corresponding voxels in the
	 * input image 'inII', and returns the most frequently occuring such
	 * label provided that the overlap of this label is more than marker's
	 * size times this.minFractionOfMarker.
	 * The function returns -1 if no such label is found.
	 */
	@Override
	public
	float findMatchingLabel(final IterableInterval<IT> inII,
	                        final IterableInterval<LT> markerII,
	                        final int markerValue)
	{
		//keep frequencies of labels discovered across the marker volume
		final HashMap<Float,Integer> labelCounter = new HashMap<>();

		final Cursor<IT> inCursor = inII.cursor();
		final Cursor<LT> markerCursor = markerII.cursor();
		int markerSize = 0;

		//find relevant label(s), if any
		while (markerCursor.hasNext())
		{
			//advance both cursors in synchrony
			inCursor.next();
			if (markerCursor.next().getInteger() == markerValue)
			{
				//we are over the original marker in the marker image,
				++markerSize;

				//check what value is in the input image
				//and update the counter of found values
				final float inVal = inCursor.get().getRealFloat();
				labelCounter.put(inVal, labelCounter.getOrDefault(inVal,0)+1);
			}
		}

		//now, find the most frequent input label...
		//(except for the background...)
		float bestLabel = -1;
		int bestCount = 0;
		for (Iterator<Float> keys = labelCounter.keySet().iterator(); keys.hasNext(); )
		{
			float curLabel = keys.next();
			if (labelCounter.get(curLabel) > bestCount && curLabel > 0)
			{
				bestLabel = curLabel;
				bestCount = labelCounter.get(curLabel);
			}
		}

		//check if the most frequent one also spans at least half
		//of the input marker volume
		return ( (bestCount > minFractionOfMarker*markerSize) ? bestLabel : -1 );
	}


	@Override
	public
	void isolateGivenLabel(final RandomAccessibleInterval<IT> sourceRAI,
	                       final float wantedLabel,
	                       final RandomAccessibleInterval<ET> outputRAI,
	                       final ET saveAsLabel)
	{
		LoopBuilder.setImages(sourceRAI,outputRAI).forEachPixel( (i,o) -> { if (i.getRealFloat() == wantedLabel) o.set(saveAsLabel); } );
	}
	

	@Override
	public
	void addGivenLabel(final RandomAccessibleInterval<IT> sourceRAI,
	                   final float wantedLabel,
	                   final RandomAccessibleInterval<ET> outputRAI,
	                   final ET addThisLabel)
	{
		LoopBuilder.setImages(sourceRAI,outputRAI).forEachPixel( (i,o) -> { if (i.getRealFloat() == wantedLabel) o.add(addThisLabel); } );
	}


	/**
	 * Determines the minimal AABB (axes-aligned bounding box) around the marker
	 * found at the cursor position. It is assumed that the cursor points on the first
	 * occurence of such marker. The object of the input cursor is not modified at all.
	 *
	 * @param mCursor   Position of the first occurence of the marker (will not be changed)
	 * @param minBound  Output "lower-left" corner of the box (must be preallocated)
	 * @param maxBound  Output "upper-right" corner of the box (must be preallocated)
	 * @return number of voxels occupied by this marker
	 */
	public static <LT extends IntegerType<LT>>
	long findAABB(final Cursor<LT> mCursor,
	              final long[] minBound, final long[] maxBound)
	{
		//time-saver: the marker we search for, and the current position array
		final int marker = mCursor.get().getInteger();
		final long[] pos = new long[minBound.length];

		//init the output variables
		long size = 0; //nothing seen yet since we reset() below
		mCursor.localize(minBound); //BB spans this one pixel yet
		mCursor.localize(maxBound);

		//working copy of the input cursor
		final Cursor<LT> cursor = mCursor.copyCursor();
		cursor.reset();
		//NB: for some reason we cannot continue from the mCursor's place, in which case
		//the cursor does not see the original image content (until we cursor.reset() it)

		//scan the rest of the image
		while (cursor.hasNext())
		{
			//the same marker?
			if (cursor.next().getInteger() == marker)
			{
				//yes, update the box corners
				cursor.localize(pos);
				for (int d = 0; d < minBound.length; ++d)
				{
					if (pos[d] < minBound[d]) minBound[d] = pos[d];
					if (pos[d] > maxBound[d]) maxBound[d] = pos[d];
				}
				++size;
			}
		}

		return size;
	}

	// ---------------- logging ----------------
	Logger log = new SimpleRestrictedLogger();
	@Override
	public void useNowThisLog(final Logger log)
	{ this.log = log; }
}
