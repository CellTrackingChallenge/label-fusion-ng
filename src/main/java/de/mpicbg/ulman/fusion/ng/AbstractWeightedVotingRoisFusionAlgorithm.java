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
package de.mpicbg.ulman.fusion.ng;

import de.mpicbg.ulman.fusion.ng.insert.LabelInsertor;
import de.mpicbg.ulman.fusion.ng.postprocess.KeepLargestCCALabelPostprocessor;
import de.mpicbg.ulman.fusion.util.ReusableMemory;
import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.loops.LoopBuilder;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.operators.SetZero;
import net.imglib2.view.Views;
import org.scijava.log.Logger;
import sc.fiji.simplifiedio.SimplifiedIO;

import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.Vector;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;

public abstract
class AbstractWeightedVotingRoisFusionAlgorithm<IT extends RealType<IT>, LT extends IntegerType<LT>, ET extends RealType<ET>>
extends AbstractWeightedVotingFusionAlgorithm<IT,LT,ET>
{
	public AbstractWeightedVotingRoisFusionAlgorithm(Logger _log, ET refType) {
		super(_log, refType);
	}

	//per image, per label, AABB as 2*imgDim-long-array
	public Vector<Map<Double,long[]>> inBoxes;
	public Map<Double,long[]> markerBoxes;

	public
	void setupBoxes(final Vector<RandomAccessibleInterval<IT>> inImgs,
	                final RandomAccessibleInterval<LT> markerImg)
	{
		setupBoxes(inImgs);
		markerBoxes = findBoxes(markerImg,log,"marker");
	}

	public
	void setupBoxes(final Vector<RandomAccessibleInterval<IT>> inImgs)
	{
		inBoxes = new Vector<>(inImgs.size());
		int cnt=0;
		for (RandomAccessibleInterval<IT> inImg : inImgs)
		{
			inBoxes.add( findBoxes(inImg,log,""+(++cnt)+".") );
		}
	}

	public
	void setupBoxes(final Vector<RandomAccessibleInterval<IT>> inImgs,
	                final RandomAccessibleInterval<LT> markerImg,
	                final ExecutorService workerThreads)
			throws InterruptedException
	{
		inBoxes = new Vector<>(inImgs.size());

		List<Callable<Object>> tasks = new ArrayList<>(inImgs.size()+1);
		for (int i = 0; i < inImgs.size(); ++i) {
			inBoxes.add(null);
			final int idx = i;
			tasks.add( () -> inBoxes.set( idx, findBoxes(inImgs.get(idx),log,""+idx+".") ) );
		}
		tasks.add( () -> markerBoxes = findBoxes(markerImg,log,"marker") );

		workerThreads.invokeAll(tasks);
	}

	static public <T extends RealType<T>>
	Map<Double,long[]> findBoxes(final RandomAccessibleInterval<T> inImg,
			final Logger log, final String imgNickName)
	{
		//aux variables for re-using
		final int numDimensions = inImg.numDimensions();
		final long[] pos = new long[numDimensions];

		final Map<Double,long[]> boxes = new HashMap<>(3000);
		log.info("pre-calculating ROIs (boxes) for "+imgNickName+" image");

		final Cursor<T> mCursor = Views.flatIterable(inImg).localizingCursor();
		while (mCursor.hasNext())
		{
			final double label = mCursor.next().getRealDouble();
			if (label > 0)
			{
				mCursor.localize(pos);

				long[] box = boxes.getOrDefault(label,null);
				if (box == null)
				{
					box = new long[2*numDimensions];
					boxes.put(label,box);

					for (int n = 0; n < numDimensions; ++n) {
						box[n] = pos[n];
						box[n+numDimensions] = pos[n];
					}
				}

				for (int n = 0; n < numDimensions; ++n) {
					if (pos[n] < box[n]) box[n] = pos[n];
					if (pos[n] > box[n+numDimensions]) box[n+numDimensions] = pos[n];
				}
			}
		}

		log.trace("done pre-calculating ROIs (boxes) for "+imgNickName+" image");
		return boxes;
	}

	static public
	void unionBoxes(final long[] box, final long[] targetBox)
	{
		final int dim = box.length / 2;
		int j = dim;
		for (int i = 0; i < dim; ++i) {
			if (box[i] < targetBox[i]) targetBox[i] = box[i];
			if (box[j] > targetBox[j]) targetBox[j] = box[j];
			++j;
		}
	}

	public
	String printBox(final double label, final long[] bbox)
	{
		final StringBuilder sb = new StringBuilder();
		sb.append("  label ").append(label).append(": [").append(bbox[0]);
		int n = 1;
		for (; n < bbox.length/2; ++n) sb.append(',').append(bbox[n]);
		sb.append("] -> [").append(bbox[n]);
		++n;
		for (; n < bbox.length; ++n) sb.append(',').append(bbox[n]);
		sb.append("]");
		return sb.toString();
	}

	public
	void printBoxes()
	{
		for (int i = 0; i < inBoxes.size(); ++i)
		{
			final Map<Double,long[]> boxes = inBoxes.get(i);
			log.info("Image "+i+":");

			for (Map.Entry<Double,long[]> box : boxes.entrySet())
				log.info(printBox(box.getKey(),box.getValue()));

			log.info("==========================");
		}

		log.info("Marker image");
		for (Map.Entry<Double,long[]> box : markerBoxes.entrySet())
			log.info(printBox(box.getKey(),box.getValue()));
		log.info("==========================");
	}

	public static
	Interval createInterval(final long[] box)
	{
		final int dim = box.length/2;
		final long[] min = new long[dim];
		final long[] max = new long[dim];
		for (int n = 0; n < dim; ++n) {
			min[n] = box[n];
			max[n] = box[n+dim];
		}
		return new FinalInterval(min,max);
	}

	@Override
	public Img<LT> fuse(final Vector<RandomAccessibleInterval<IT>> inImgs,
	                    final Img<LT> markerImg)
	{
		if (inImgs.size() != inWeights.size())
			throw new RuntimeException("Arrays with input images and weights are of different lengths.");

		if (labelExtractor == null || labelFuser == null || labelInsertor == null || labelCleaner == null)
			throw new RuntimeException("Object is not fully and properly initialized.");

		//da plan:
		//iterate over all voxels of the input marker image and look for not
		//yet found marker, and for every such new discovered, do:
		//from all input images extract all labelled components that intersect
		//with the marker in more than half of the total marker voxels, combine
		//these components and threshold according to the given input threshold
		//(the 3rd param), save this thresholded component under the discovered marker
		//
		//while saving the marker, it might overlap with some other already
		//saved marker; mark such voxels specifically in the output image for
		//later post-processing

		//create a temporary image (of the same iteration order as the markerImg)
		log.info("tmpImg: "+reportImageSize(markerImg));
		log.info("outImg: "+reportImageSize(markerImg,2));
		log.info("borrowing tmp+out (2) images...");
		final ReusableMemory<LT, ET> MEMORY = ReusableMemory.getInstanceFor(markerImg, markerImg.firstElement(), referenceType);
		final Img<ET> tmpImg = MEMORY.getTmpImg( ReusableMemory.getThreadId() );
		log.trace("borrowed tmpImg");

		//create the output image (of the same iteration order as the markerImg),
		//and init it
		final Img<LT> outImg = MEMORY.getOutImg( ReusableMemory.getThreadId() );
		log.trace("borrowed outImg");
		LoopBuilder.setImages(outImg).forEachPixel(SetZero::setZero);
		log.trace("zeroed outImg");

		//aux params for the fusion
		final Vector<RandomAccessibleInterval<IT>> selectedInImgs  = new Vector<>(inWeights.size());
		final Vector<Float>                       selectedInLabels = new Vector<>(inWeights.size());
		log.trace("init A");

		//set to remember already discovered TRA markers
		//(with initial capacity set for 100 markers)
		Set<Integer> mDiscovered = new HashSet<>(100);
		Map<Integer,Interval> mFusedROI = new HashMap<>(100);
		log.trace("init B");

		//init insertion (includes to create (re-usable) insertion status object)
		final LabelInsertor.InsertionStatus insStatus = new LabelInsertor.InsertionStatus();
		log.info("initializing the collision-aware insertor...");
		labelInsertor.initialize(outImg);
		log.trace("init D");

		//sweep over the marker image
		log.trace("starting the main sweep");
		for (Map.Entry<Double,long[]> marker : markerBoxes.entrySet())
		{
			final int curMarker = marker.getKey().intValue();

			//scan for not yet observed markers (and ignore background values...)
			if ( curMarker > 0
					&& !mDiscovered.contains(curMarker) //NB: this should never happen...
					&& !ignoredMarkersTemporarily.contains(curMarker)
					&& !ignoredMarkersPermanently.contains(curMarker) )
			{
				log.trace("processing next marker: "+curMarker);
				//
				//found next marker, copy out the AABB it spans over
				final long[] fuseBox = marker.getValue().clone();
				log.trace("found its AABB: "+printBox(curMarker,fuseBox));

				//sweep over all input images
				final Interval mInterval = createInterval(fuseBox);
				selectedInImgs.clear();
				selectedInLabels.clear();
				int noOfMatchingImages = 0;
				for (int i = 0; i < inImgs.size(); ++i)
				{
					log.trace("searching input image "+i+" for candidate");
					//find the corresponding label in the input image (in the restricted interval)
					final float matchingLabel = labelExtractor.findMatchingLabel(
							Views.interval(inImgs.get(i), mInterval),
							Views.interval(markerImg,     mInterval),
							curMarker);
					log.trace("finished the searching, found "+matchingLabel);

					if (matchingLabel > 0)
					{
						selectedInImgs.add(inImgs.get(i));
						selectedInLabels.add(matchingLabel);
						++noOfMatchingImages;
						unionBoxes(inBoxes.get(i).get((double)matchingLabel),fuseBox);
						log.trace("AABB of candidate: "+printBox(matchingLabel,inBoxes.get(i).get((double)matchingLabel)));
						log.trace("fuse AABB updated: "+printBox(curMarker,fuseBox));
					}
					else
					{
						selectedInImgs.add(null);
						selectedInLabels.add(0.f);
					}
				}

				if (noOfMatchingImages > 0)
				{
					//process within the union'ed interval (of candidates' boxes)
					final Interval fuseInterval = createInterval(fuseBox);
					mFusedROI.put(curMarker,fuseInterval);

					//reset the temporary image beforehand
					LoopBuilder.setImages(Views.interval(tmpImg,fuseInterval)).forEachPixel(SetZero::setZero);
					log.trace("zeroed tmpImg");

					//fuse the selected labels into it
					labelFuser.fuseMatchingLabels(selectedInImgs,selectedInLabels,
					                              labelExtractor,inWeights, tmpImg, fuseInterval);
					log.trace("fused into tmpImg");

					//save the debug image
					//SimplifiedIO.saveImage(tmpImg, "/Users/ulman/DATA/dbgMerge__"+curMarker+".tif");

					//insert the fused segment into the output image
					labelInsertor.insertLabel(Views.interval(tmpImg,fuseInterval),
							Views.interval(outImg,fuseInterval),curMarker, insStatus);
				}
				else
				{
					insStatus.clear();
					labelInsertor.mCollidingVolume.put(curMarker,0L);
					labelInsertor.mNoCollidingVolume.put(curMarker,0L);
				}

				//some per marker report:
				String markerReport = "TRA marker: "+curMarker+" , images matching: "+noOfMatchingImages;

				//outcomes in 4 states:
				//TRA marker was secured (TODO: secured after threshold increase)
				//TRA marker was hit but removed due to collision, or due to border
				//TRA marker was not hit at all

				//also note the outcome of this processing, which is exclusively:
				//found, not found, in collision, at border
				if (!insStatus.foundAtAll)
				{
					labelInsertor.mNoMatches.add(curMarker);
					log.info(markerReport+" , not included because not matched in results");
				}
				else
				{
					if (removeMarkersAtBoundary & insStatus.atBorder)
					{
						labelInsertor.mBordering.add(curMarker);
						log.info(markerReport+" , detected to be at boundary");
					}
					else if (insStatus.inCollision)
						//NB: labelInsertor.mColliding.add() must be done after all markers are processed
						log.info(markerReport+" , detected to be in collision");
					else
						log.info(markerReport+" , secured for now");
				}

				if (insStatus.localColliders.size() > 0)
				{
					StringBuilder sb = new StringBuilder("guys colliding with this marker: ");
					for (int integer : insStatus.localColliders) sb.append(integer).append(',');
					log.info(sb.toString());
				}

				//finally, mark we have processed this marker
				mDiscovered.add(curMarker);
			} //after marker processing
			else
			{
				log.error("SOMETHING WEIRD!");
			}
		} //after all voxel looping

		//save now a debug image
		if (dbgImgFileName != null && dbgImgFileName.length() > 0)
		{
			SimplifiedIO.saveImage(outImg, dbgImgFileName);
		}

		log.info("resolving the left-out collisions...");
		final int allMarkers = mDiscovered.size();
		final int[] collHistogram
			= labelInsertor.finalize(outImg,markerImg,removeMarkersCollisionThreshold,removeMarkersAtBoundary);

		if (dbgImgFileName != null && dbgImgFileName.length() > 0)
		{
			int dotPos = dbgImgFileName.lastIndexOf('.');
			SimplifiedIO.saveImage(outImg,
				dbgImgFileName.substring(0,dotPos) + "_afterFinalize" + dbgImgFileName.substring(dotPos) );
		}

		// --------- CCA analyses ---------
		mDiscovered.clear();
		final int collisionValue = labelInsertor.getValueOfCollisionPixels();
		final Cursor<LT> outFICursor = outImg.cursor();
		while (outFICursor.hasNext())
		{
			final int curMarker = outFICursor.next().getInteger();

			//wipe-out leftovers from post-processing, and scan for
			//not yet observed markers (and ignore background values...)
			if (curMarker == collisionValue) outFICursor.get().setZero();
			else if ( curMarker > 0 && (!mDiscovered.contains(curMarker)) )
			{
				labelCleaner.processLabel(outImg, curMarker, mFusedROI.get(curMarker));

				//and mark we have processed this marker
				mDiscovered.add(curMarker);
			}
		}
		if (labelCleaner instanceof KeepLargestCCALabelPostprocessor) {
			//only after the all cleaning is done....
			((KeepLargestCCALabelPostprocessor<LT>)labelCleaner).releaseBorrowedMem();
		}
		// --------- CCA analyses ---------

		//report details of colliding markers:
		log.info("reporting colliding markers:");
		for (final int marker : labelInsertor.mCollidingVolume.keySet())
		{
			float collRatio = (float) labelInsertor.mCollidingVolume.get(marker);
			collRatio /= (float) (labelInsertor.mNoCollidingVolume.get(marker) + labelInsertor.mCollidingVolume.get(marker));
			if (collRatio > 0.f)
				log.info("marker: " + marker + ": colliding " + labelInsertor.mCollidingVolume.get(marker)
						+ " and non-colliding " + labelInsertor.mNoCollidingVolume.get(marker)
						+ " voxels ( " + collRatio + " ) "
						+ (collRatio > removeMarkersCollisionThreshold ? "too much" : "acceptable"));
		}

		//report the histogram of colliding volume ratios
		for (int hi=0; hi < 10; ++hi)
			log.info("HIST: "+(hi*10)+" %- "+(hi*10+9)+" % collision area happened "
			                  +collHistogram[hi]+" times");
		log.info("HIST: 100 %- 100 % collision area happened "
		                  +collHistogram[10]+" times");

		//also some per image report:
		final int okMarkers = allMarkers - labelInsertor.mNoMatches.size() - labelInsertor.mBordering.size() - labelInsertor.mColliding.size();
		log.info("not found markers    = "+labelInsertor.mNoMatches.size()
			+" = "+ 100.0f*(float)labelInsertor.mNoMatches.size()/(float)allMarkers +" %");
		log.info("markers at boundary  = "+labelInsertor.mBordering.size()
			+" = "+ 100.0f*(float)labelInsertor.mBordering.size()/(float)allMarkers +" %");
		log.info("markers in collision = "+labelInsertor.mColliding.size()
			+" = "+ 100.0f*(float)labelInsertor.mColliding.size()/(float)allMarkers +" %");
		log.info("secured markers      = "+okMarkers
			+" = "+ 100.0f*(float)okMarkers/(float)allMarkers +" %");

		if (insertTRAforCollidingOrMissingMarkers && (labelInsertor.mColliding.size() > 0 || labelInsertor.mNoMatches.size() > 0))
		{
			//sweep the output image and add missing TRA markers
			//
			//TODO: accumulate numbers of how many times submitting of TRA label
			//would overwrite existing label in the output image, and report it
			LoopBuilder.setImages(outImg,markerImg).forEachPixel(
				(o,m) -> {
					final int outLabel = o.getInteger();
					final int traLabel = m.getInteger();
					if (outLabel == 0 && (labelInsertor.mColliding.contains(traLabel) || labelInsertor.mNoMatches.contains(traLabel)))
						o.setInteger(traLabel);
				} );
		}

		ignoredMarkersTemporarily.clear();
		return outImg;
	}
}
