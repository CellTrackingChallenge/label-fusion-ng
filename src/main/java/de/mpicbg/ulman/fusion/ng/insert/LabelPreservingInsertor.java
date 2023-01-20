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
package de.mpicbg.ulman.fusion.ng.insert;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.loops.LoopBuilder;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;

public class LabelPreservingInsertor<LT extends IntegerType<LT>, ET extends RealType<ET>>
extends CollisionsAwareLabelInsertor<LT,ET>
implements LabelInsertor<LT,ET>
{
	@Override
	public
	void initialize(final Img<LT> templateImg)
	{ /* intentionally empty */ }

	private
	final int[] collHistogram = new int[11];

	@Override
	public
	int[] finalize(final Img<LT> outImg, final Img<LT> markerImg,
	               final float removeMarkersCollisionThreshold,
	               final boolean removeMarkersAtBoundary)
	{
		/* intentionally empty */
		return collHistogram;
	}

	/**
	 * Copies non-zero pixels (copies their values) from 'inSingleLabelImg'
	 * into 'outResultImg', ignoring 'outMarker', and down-rounding any real
	 * number from the input for the integer-valued output.
	 *
	 * It inserts only non-zero pixels. While inserting, it overwrites anything
	 * in the output image.
	 *
	 * The 'status' is left untouched, just like it is done in the
	 * {@link OverwriteLabelInsertor::insertLabel()}.
	 */
	@Override
	public
	void insertLabel(final RandomAccessibleInterval<ET> inSingleLabelImg,
	                 final RandomAccessibleInterval<LT> outResultImg,
	                 final int outMarker,
	                 final InsertionStatus status)
	{
		LoopBuilder.setImages(outResultImg,inSingleLabelImg)
				.forEachPixel( (io,lab) -> {
					final int lVal = (int)lab.getRealFloat();
					if (lVal > 0) io.setInteger(lVal);
				} );
	}
}