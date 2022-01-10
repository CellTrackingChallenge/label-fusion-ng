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
package de.mpicbg.ulman.fusion.ng.insert;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.IntegerType;
import java.util.HashSet;

/**
 * Inserts the labels (into image of voxel types LT -- Label Type) from (possibly a working)
 * image (of voxel types ET -- Extracted_as Type) with individual fused segment.
 *
 * @param <LT>  voxel type of the marker/label image (on which the labels are synchronized)
 * @param <ET>  voxel type of the output image (into which the input labels are extracted)
 */
public interface LabelInsertor<LT extends IntegerType<LT>, ET extends RealType<ET>>
{
	class InsertionStatus
	{
		/** was the marker found in the source image at all? */
		public boolean foundAtAll;

		/** is the marker touching image boundary? */
		public boolean atBorder;

		/** is the marker overlapping/colliding with some other marker in the output image? */
		public boolean inCollision;

		/** list of other markers in the output image that would overlap with this one */
		public HashSet<Integer> localColliders = new HashSet<>(100);

		/** volume of the marker that was easily inserted, i.e. with out any collision/overlap */
		public long collidingVolume;
		/** volume of the marker that is in collision/overlap with some other marker */
		public long notCollidingVolume;

		public
		void clear()
		{
			foundAtAll = false;
			atBorder = false;
			inCollision = false;
			localColliders.clear();
			collidingVolume = 0;
			notCollidingVolume = 0;
		}
	}

	/**
	 * It is assumed that any non-zero pixel from the 'inSingleLabelImg'
	 * will be inserted into the 'outResultImg' with a value of 'outMarker'.
	 * The information regarding the insertion is stored in the 'operationStatus',
	 * if caller supplies it (yes, that parameter may be set to null).
	 */
	void insertLabel(final RandomAccessibleInterval<ET> inSingleLabelImg,
	                 final RandomAccessibleInterval<LT> outResultImg,
	                 final int outMarker,
	                 final InsertionStatus operationStatus);

	/**
	 * Some implementations of the {@link #insertLabel(RAI,RAI,int,InsertionStatus)}
	 * may detect when insertion of an label may lead to overwriting of some other
	 * previously inserted label -- a collision situation, and some implementations
	 * may mark/denote such "colliding pixels" with a special value. This method
	 * allows to read what is that special value.
	 */
	int getValueOfCollisionPixels();
}
