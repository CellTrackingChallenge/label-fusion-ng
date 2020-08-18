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
package de.mpicbg.ulman.fusion.ng.insert;

import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.Cursor;
import net.imglib2.img.Img;
import net.imglib2.view.Views;
import net.imglib2.loops.LoopBuilder;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

public class CollisionsAwareLabelInsertor<LT extends IntegerType<LT>, ET extends RealType<ET>>
implements LabelInsertor<LT,ET>
{
	/** number of colliding voxels per marker,
	    NB: used to determine portion of the colliding volume */
	public HashMap<Integer,Long> mCollidingVolume = new HashMap<>(100);

	/** number of non-colliding voxels per marker,
	    NB: used to determine portion of the colliding volume */
	public HashMap<Integer,Long> mNoCollidingVolume = new HashMap<>(100);

	/** set of markers that are in some collision */
	public HashSet<Integer> mColliding = new HashSet<>(100);

	/** set of markers that are touching output image border */
	public HashSet<Integer> mBordering = new HashSet<>(100);

	/** set of markers for which not enough of input segments were found */
	public HashSet<Integer> mNoMatches = new HashSet<>(100);

	/** special label for the voxels in the "collision area" of more labels */
	protected int INTERSECTION;

	/** aux temporary storage of position, created to prevent from
	    iteratively allocating it anywhere in this and derived classes */
	protected final int[] pos = new int[3];


	public
	void initialize(final Img<LT> templateImg)
	{
		mCollidingVolume.clear();
		mNoCollidingVolume.clear();

		mColliding.clear();
		mBordering.clear();
		mNoMatches.clear();

		INTERSECTION = (int)(templateImg.firstElement().getMaxValue());
	}


	/** returns the collision size histogram */
	public
	int[] finalize(final Img<LT> outImg, final Img<LT> markerImg,
	               final float removeMarkersCollisionThreshold,
	               final boolean removeMarkersAtBoundary)
	{
		//check colliding markers and decide if to be removed or not
		//and fill a histogram array at the same time
		final int[] collHistogram = new int[11];
		for (Iterator<Integer> it = mCollidingVolume.keySet().iterator(); it.hasNext(); )
		{
			final int marker = it.next();

			//get proportion of colliding volume from the whole marker volume
			float collRatio = (float)mCollidingVolume.get(marker);
			collRatio /= (float)(mNoCollidingVolume.get(marker)+mCollidingVolume.get(marker));

			//decide if to mark the marker for removal
			if ( (collRatio > removeMarkersCollisionThreshold)
			  && (!mBordering.contains(marker)) ) mColliding.add(marker);
			  //NB: should not be in two classes simultaneously

			//update the histogram
			if (!mNoMatches.contains(marker))
				collHistogram[(int)(collRatio*10.f)]++;
		}

		//jobs: remove border-touching cells
		//jobs: remove colliding cells
		//sweep the output image and do the jobs
		LoopBuilder.setImages(outImg).forEachPixel(
			(a) -> {
				final int label = a.getInteger();
				if (label == INTERSECTION)
				{
					a.setZero();
					//System.out.println("cleaning: collision intersection");
				}
				else if (mColliding.contains(label))
				{
					a.setZero();
					//System.out.println("cleaning: rest of a colliding marker");
				}
				else if (removeMarkersAtBoundary && mBordering.contains(label))
				{
					a.setZero();
					//System.out.println("cleaning: marker at boundary");
				}
			} );

		return collHistogram;
	}


	/** status-less wrapper around this.insertLabel() */
	public
	void insertLabel(final Img<ET> inSingleLabelImg,
	                 final Img<LT> outResultImg,
	                 final int outMarker)
	{
	    insertLabel(inSingleLabelImg,outResultImg,outMarker, fakeStatus);
	}
	private static InsertionStatus fakeStatus = new InsertionStatus();


	/**
	 * It is assumed that any non-zero pixel from the 'inSingleLabelImg'
	 * will be inserted into the 'outResultImg' with a value of 'outMarker'.
	 * The information regarding the insertion is stored in the 'operationStatus'.
	 */
	@Override
	public
	void insertLabel(final Img<ET> inSingleLabelImg,
	                 final Img<LT> outResultImg,
	                 final int outMarker,
	                 final InsertionStatus status)
	{
		status.clear();

		//now, threshold the tmp image (provided we have written there something
		//at all) and store it with the appropriate label in the output image
		final Cursor<ET> tmpFICursor = Views.flatIterable( inSingleLabelImg ).cursor();
		final Cursor<LT> outFICursor = Views.flatIterable( outResultImg ).localizingCursor();

		while (outFICursor.hasNext())
		{
			outFICursor.next();
			if (tmpFICursor.next().getRealFloat() > 0)
			{
				//voxel to be inserted into the output final label mask
				status.foundAtAll = true;

				final int otherMarker = outFICursor.get().getInteger();
				if (otherMarker == 0)
				{
					//inserting into an unoccupied voxel
					outFICursor.get().setInteger(outMarker);
					status.notCollidingVolume++;
				}
				else
				{
					//collision detected
					outFICursor.get().setInteger(INTERSECTION);
					status.collidingVolume++;
					status.inCollision = true;

					outFICursor.localize(pos);
					registerPxInCollision(pos, outMarker);

					if (otherMarker != INTERSECTION)
					{
						status.localColliders.add(otherMarker);
						registerPxInCollision(pos, otherMarker);

						//update also stats of the other guy
						//because he was not intersecting here previously
						mNoCollidingVolume.put(otherMarker,mNoCollidingVolume.get(otherMarker)-1);
						mCollidingVolume.put(otherMarker,mCollidingVolume.get(otherMarker)+1);
					}
				}

				//check if we are at the image boundary
				for (int i = 0; i < outResultImg.numDimensions() && !status.atBorder; ++i)
					if ( outFICursor.getLongPosition(i) == outResultImg.min(i)
					  || outFICursor.getLongPosition(i) == outResultImg.max(i) ) status.atBorder = true;
			}
		}

		mCollidingVolume.put(  outMarker,status.collidingVolume);
		mNoCollidingVolume.put(outMarker,status.notCollidingVolume);
	}

	/**
	 * A callback method called from {@link #insertLabel(Img, Img, int, InsertionStatus)} every time
	 * it creates a pixel (at position 'pos') in collision. Derived classes are expected to override
	 * this method and use it to store additional information regarding the collision. This class only
	 * marks the pixels in the output image with the value of {@link #INTERSECTION}.
	 *
	 * @param pos      coordinate of the pixel found to be in collision
	 * @param claimer  label that wanted to place itself at this pixel
	 */
	void registerPxInCollision(final int[] pos, final int claimer)
	{ /* intentionally empty */ }
}
