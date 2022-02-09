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

import net.imglib2.loops.LoopBuilder;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.integer.UnsignedIntType;
import net.imglib2.Cursor;
import net.imglib2.RandomAccess;
import net.imglib2.img.Img;
//import sc.fiji.simplifiedio.SimplifiedIO;

import java.util.*;

public class CollisionsManagingLabelInsertor<LT extends IntegerType<LT>, ET extends RealType<ET>>
extends CollisionsAwareLabelInsertor<LT,ET>
implements LabelInsertor<LT,ET>
{
	class PxCoord implements Comparator<PxCoord>
	{
		PxCoord(int... xyz)
		{
			this.x = xyz[0];
			this.y = xyz[1];
			this.z = xyz[2];
		}
		PxCoord(int color, int... xyz)
		{
			this.color = color;
			this.x = xyz[0];
			this.y = xyz[1];
			this.z = xyz[2];
		}

		int x, y, z;

		/** assuming pixels are organized row-major in big memory (1D) buffer,
		    this compares how far are two pixels away from the [0,0,0] in the buffer */
		@Override
		public int compare(PxCoord p1, PxCoord p2)
		{
			int delta = p1.z - p2.z;
			if (delta == 0)
			{
				delta = p1.y - p2.y;
				if (delta == 0)
					delta = p1.x - p2.x;
			}
			return delta;
		}

		/** marker color finally decided to be used for this pixel */
		int color = -1;

		/** list of markers/labels that wanted to be on this pixel */
		Set<Integer> claimingLabels = new HashSet<>(5);
	}

	@Override
	void registerPxInCollision(final int[] pos, final int claimer)
	{
		//find coinciding pixel
		PxCoord p;

		//check the map first
		pxInINTERSECTION_map_RA.setPosition(pos);
		int pxCoordIdx = pxInINTERSECTION_map_RA.get().getInt();

		if (pxCoordIdx > 0)
		{
			//found coinciding pixel, add another claimer
			p = pxInINTERSECTION.get( pxCoordIdx-1 );
			p.claimingLabels.add( claimer );
			//TODO remove debug test
			if (p.x != pos[0] || p.y != pos[1] || p.z != pos[2])
				log.warn("WARNING: pxInINTERSECTION map refers to wrong PxCoord!");
			return;
		}

		//not found, add a brand new pixel with its claimer
		p = new PxCoord(pos);
		p.claimingLabels.add( claimer );
		pxInINTERSECTION.add( p );
		pxInINTERSECTION_map_RA.get().setInt( pxInINTERSECTION.size() );
		//
		if (pxInINTERSECTION.size() == pxInINTERSECTION_map.firstElement().getMaxValue())
			log.warn("WARNING: pxInINTERSECTION map cannot hold more PxCoords!");
	}

	List<PxCoord> pxInINTERSECTION;
	List<PxCoord> pxTemporarilyHidden;

	protected Img<UnsignedIntType> pxInINTERSECTION_map;
	protected RandomAccess<UnsignedIntType> pxInINTERSECTION_map_RA;

	@Override
	public
	void initialize(final Img<LT> templateImg)
	{
		super.initialize(templateImg);

		pxInINTERSECTION = new Vector<>(500000);
		pxTemporarilyHidden = new LinkedList<>();

		pxInINTERSECTION_map
			= templateImg.factory().imgFactory(new UnsignedIntType()).create(templateImg);
		LoopBuilder.setImages(pxInINTERSECTION_map).forEachPixel(UnsignedIntType::setZero);
		pxInINTERSECTION_map_RA = pxInINTERSECTION_map.randomAccess();
	}

	/** returns the collision size histogram */
	@Override
	public
	int[] finalize(final Img<LT> outImg, final Img<LT> markerImg,
	               final float removeMarkersCollisionThreshold,
	               final boolean removeMarkersAtBoundary)
	{
		//check colliding markers and decide if to be removed or not
		//and fill a histogram array at the same time
		final int[] collHistogram = new int[11];
		for (int marker : mCollidingVolume.keySet())
		{
			//get proportion of colliding volume from the whole marker volume
			float collRatio = (float)mCollidingVolume.get(marker);
			collRatio /= (float)(mNoCollidingVolume.get(marker)+mCollidingVolume.get(marker));

			//decide if to mark the marker for removal
			//NB: should not be in two classes simultaneously
			if ( (collRatio > removeMarkersCollisionThreshold) && (!mBordering.contains(marker)) )
			{
				mColliding.add(marker);
			}

			//update the histogram
			if (!mNoMatches.contains(marker))
				collHistogram[(int)(collRatio*10.f)]++;
		}

		//job #1: remove border-touching cells
		//job #3: insert TRA markers for those in mColliding
		//job #4: move pixels from mColliding to pxTemporarilyHidden
		//sweep the output image and do the jobs
		final Cursor<LT> oC = outImg.localizingCursor();
		final Cursor<LT> mC = markerImg.cursor();
		//NB: we have to be sweeping explicitly (w/o LoopBuilder) because we need to know
		//    coords where we are (and we gonna be storing them in the aux array 'pos')
		//NB: assumes that markerImg was created from outImg and both have, thus, the same iteration order
		while (oC.hasNext())
		{
			final LT o = oC.next();
			final LT m = mC.next();
			final int label = o.getInteger();
			if (removeMarkersAtBoundary && mBordering.contains(label))
			{
				o.setZero(); //job #1
			}
			else if (label == INTERSECTION)
			{
				final int mLabel = m.getInteger();
				if (mLabel > 0 && mColliding.contains(mLabel)) o.setReal(mLabel); //job #3
			}
			else if (mColliding.contains(label))
			{
				oC.localize(pos);
				pxTemporarilyHidden.add( new PxCoord(label, pos) ); //job #4
				o.setZero();                                        //job #4
			}
		}

		//sort to have the list of coords memory(cache)-friendly
		if (pxInINTERSECTION.size() > 0)
			pxInINTERSECTION.sort( pxInINTERSECTION.get(0) );

		//debug img:
		//SimplifiedIO.saveImage(outImg,"/temp/X_before.tif");
		//int cnt = 0;

		//fill in the intersection region by iterative eroding it,
		//eroding it only with its claimers that are neighboring to it
		//
		//preparations:
		final RandomAccess<LT> oRA = outImg.randomAccess();
		final int[] posMax = new int[3]; //note, it comes zeroed
		for (int d = 0; d < outImg.numDimensions() && d < posMax.length; ++d)
			posMax[d] = (int)outImg.dimension(d) -1; //to make it a max legal coord
		//
		//do as long as all collision pixels are resolved,
		//resolved means that its label is determined, or
		//we cannot determine it under the current circumstances
		int lastSize = pxInINTERSECTION.size() +1;
		int safetyCounter = 100;
		while (pxInINTERSECTION.size() > 0 && pxInINTERSECTION.size() != lastSize && --safetyCounter > 0)
		{
			//debug:
			//log.trace(cnt+": Eroding collision zone of size "+pxInINTERSECTION.size());

			lastSize = pxInINTERSECTION.size();
			erodeCollisionRegion(oRA, posMax);

			//debug img:
			//SimplifiedIO.saveImage(outImg, String.format("/temp/X_round%d.tif",++cnt) );
		}

		// return back the temporarily hidden pixels
		for (PxCoord px : pxTemporarilyHidden)
		{
			pos[0] = px.x;
			pos[1] = px.y;
			pos[2] = px.z;
			oRA.setPosition(pos);
			oRA.get().setReal( px.color );
		}

		//sometimes, when mColliding-label's TRA marker was outside the pxInINTERSECTION,
		//the "erosion" of INTERSECTION region stalled and we have to restart it now --
		//now after the pxTemporarilyHidden pixels are back
		safetyCounter = 100;
		while (pxInINTERSECTION.size() > 0 && --safetyCounter > 0)
		{
			erodeCollisionRegion(oRA, posMax);
		}

		if (pxInINTERSECTION.size() > 0)
			log.warn("WARNING: Collisions resolving failed on "+pxInINTERSECTION.size()+" voxels");

		return collHistogram;
	}

	private
	void erodeCollisionRegion(RandomAccess<LT> oRA, int[] posMax)
	{
		//erosion in two loops:
		//  first, determine pixels and store them aside so they don't influence the rest of the loop
		//  second, move the determined pixels into the output image
		Iterator<PxCoord> pxIt = pxInINTERSECTION.iterator();
		while (pxIt.hasNext())
		{
			PxCoord px = pxIt.next();
			//look around pxPos to find first pixel from claimers, if at all
			for (int[] posDelta : posDeltas)
			{
				pos[0] = Math.min( Math.max(px.x + posDelta[0],0) , posMax[0] );
				pos[1] = Math.min( Math.max(px.y + posDelta[1],0) , posMax[1] );
				pos[2] = Math.min( Math.max(px.z + posDelta[2],0) , posMax[2] );
				oRA.setPosition(pos);
				final int surroundingLabel = oRA.get().getInteger();
				if ( px.claimingLabels.contains(surroundingLabel) )
				{
					px.color = surroundingLabel;
					break;
				}
			}
		}
		pxIt = pxInINTERSECTION.iterator();
		while (pxIt.hasNext())
		{
			PxCoord px = pxIt.next();
			//is the pixel determined already?
			if (px.color > -1)
			{
				pos[0] = px.x;
				pos[1] = px.y;
				pos[2] = px.z;
				oRA.setPosition(pos);
				oRA.get().setReal( px.color );
				pxIt.remove();
			}
		}
	}

	final static int[][] posDeltas = new int[][] {
			{-1,-1,-1},{0,-1,-1},{1,-1,-1},  {-1,0,-1},{0,0,-1},{1,0,-1},  {-1,+1,-1},{0,+1,-1},{1,+1,-1},
			{-1,-1, 0},{0,-1, 0},{1,-1, 0},  {-1,0, 0},         {1,0, 0},  {-1,+1, 0},{0,+1, 0},{1,+1, 0},
			{-1,-1,+1},{0,-1,+1},{1,-1,+1},  {-1,0,-1},{0,0,+1},{1,0,+1},  {-1,+1,+1},{0,+1,+1},{1,+1,+1} };
}
