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

import net.imglib2.IterableInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.Cursor;
import java.util.HashMap;
import org.scijava.log.Logger;

/**
 * Detects labels in input images (of voxel type IT -- Input Type) that match given marker,
 * which is to be found in the marker image (of voxel type LT -- Label Type); and extracts
 * the input markers into some (possibly) other image (of voxel type ET -- Extract_as Type).
 *
 * @param <IT>  voxel type of input images (with labels to be fused together)
 * @param <LT>  voxel type of the marker/label image (on which the labels are synchronized)
 * @param <ET>  voxel type of the output image (into which the input labels are extracted)
 */
public interface LabelExtractor<IT extends RealType<IT>, LT extends IntegerType<LT>, ET extends RealType<ET>>
{
	/**
	 * Just returns the value of the matching label. Typically this could be the label
	 * that overlaps the 'inMarker' the most (compared to other overlapping markers).
	 *
	 * @param inII          Sweeper of the input image (from which label is to be returned)
	 * @param markerII      Sweeper of the input marker image
	 * @param markerValue   Marker (from the input marker image) in question...
	 */
	float findMatchingLabel(final IterableInterval<IT> inII,
	                        final IterableInterval<LT> markerII,
	                        final int markerValue);

	/**
	 * Just returns the all matching labels together with their overlap ratios.
	 *
	 * The default implementation works as follows:
	 * Sweeps over 'markerValue' labelled voxels inside the marker image
	 * 'markerII', checks labels found in the corresponding voxels in the
	 * input image 'inII', and returns all labels that overlap in at least
	 * one voxel. The function returns -1 if no such label is found.
	 *
	 * @param inII          Sweeper of the input image (from which label is to be returned)
	 * @param markerII      Sweeper of the input marker image
	 * @param markerValue   Marker (from the input marker image) in question...
	 */
	static <IT extends RealType<IT>, LT extends IntegerType<LT>>
	HashMap<Float,Float> findAllMatchingLabels(final IterableInterval<IT> inII,
	                                           final IterableInterval<LT> markerII,
	                                           final int markerValue)
	{
		//keep frequencies of labels discovered across the marker volume
		final HashMap<Float,Float> labelCounter = new HashMap<>();

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
				labelCounter.put(inVal, labelCounter.getOrDefault(inVal,0.f)+1);
			}
		}

		//now, compute the ratios
		//(except for the background..., NB: remove() does not comlain if key does not exist)
		labelCounter.remove(0.f);
		for (float lbl : labelCounter.keySet())
			labelCounter.put(lbl, labelCounter.get(lbl)/(float)markerSize);

		return labelCounter;
	}

	/**
	 * Just finds pixels of 'wantedLabel' value and sets the corresponding pixels
	 * to 'saveAsLabel' value in the output image.
	 */
	void isolateGivenLabel(final RandomAccessibleInterval<IT> sourceRAI,
	                       final float wantedLabel,
	                       final RandomAccessibleInterval<ET> outputRAI,
	                       final ET saveAsLabel);

	/**
	 * Just finds pixels of 'wantedLabel' value and increases the value of the
	 * corresponding pixels with 'addThisLabel' value in the output image.
	 */
	void addGivenLabel(final RandomAccessibleInterval<IT> sourceRAI,
	                   final float wantedLabel,
	                   final RandomAccessibleInterval<ET> outputRAI,
	                   final ET addThisLabel);

	// ---------------- logging ----------------
	void useNowThisLog(final Logger log);
}
