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

import net.imglib2.Cursor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.view.Views;
import net.imglib2.loops.LoopBuilder;
import net.imglib2.type.operators.SetZero;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.IntegerType;
import org.scijava.log.Logger;
import sc.fiji.simplifiedio.SimplifiedIO;

import java.util.Set;
import java.util.HashSet;
import java.util.Vector;

import de.mpicbg.ulman.fusion.ng.backbones.WeightedVotingFusionAlgorithm;
import de.mpicbg.ulman.fusion.ng.extract.LabelExtractor;
import de.mpicbg.ulman.fusion.ng.extract.MajorityOverlapBasedLabelExtractor;
import de.mpicbg.ulman.fusion.ng.fuse.LabelFuser;
import de.mpicbg.ulman.fusion.ng.insert.LabelInsertor;
import de.mpicbg.ulman.fusion.ng.insert.CollisionsAwareLabelInsertor;
import de.mpicbg.ulman.fusion.ng.postprocess.LabelPostprocessor;

/**
 * Skeleton that iterates over the individual markers from the marker image,
 * extracts the marker and collects incident segmentation masks (labels) from
 * the input images (using some method from the 'extract' folder), fuses them
 * collected labels (using some method from the 'fuse' folder), and inserts
 * the fused (created) segment (using some method from the 'insert' folder),
 * and finally cleans up the results after all of them are inserted (using some
 * method from the 'postprocess' folder).
 */
public abstract
class AbstractWeightedVotingFusionAlgorithm<IT extends RealType<IT>, LT extends IntegerType<LT>, ET extends RealType<ET>>
implements WeightedVotingFusionAlgorithm<IT,LT>
{
	///prevent from creating the class without any connection
	@SuppressWarnings("unused")
	private
	AbstractWeightedVotingFusionAlgorithm()
	{ log = null; referenceType = null; } //this is to get rid of some warnings

	protected final Logger log;
	protected final ET referenceType;

	public
	AbstractWeightedVotingFusionAlgorithm(final Logger _log, final ET refType)
	{
		if (_log == null)
			throw new RuntimeException("Please, give me existing Logger.");
		log = _log;

		if (refType == null)
			throw new RuntimeException("Provide pixel type -- precision of the fusion.");
		referenceType = refType.createVariable();

		//setup the required components
		setFusionComponents();

		//inevitable sanity test to see if the user has
		//implemented the setFusionComponents() correctly
		testFusionComponents();
	}


	private
	void testFusionComponents()
	{
		if (labelExtractor == null)
			throw new RuntimeException("this.labelExtractor must be set");

		if (labelFuser == null)
			throw new RuntimeException("this.labelFuser must be set");

		if (labelInsertor == null)
			throw new RuntimeException("this.labelInsertor must be set");

		if (labelCleaner == null)
			throw new RuntimeException("this.labelCleaner must be set");

		//pass forward the logger...
		labelExtractor.useNowThisLog(log);
		labelFuser.useNowThisLog(log);
		labelInsertor.useNowThisLog(log);
		labelCleaner.useNowThisLog(log);
	}

	/** Any class that extends this one must implement this method.
	    The purpose of this method is to define this.labelExtractor,
	    this.labelFuser, this.labelInsertor and this.labelCleaner. */
	protected abstract
	void setFusionComponents();

	//setup extract, fuse, insert, postprocess (clean up)
	LabelExtractor<IT,LT,ET> labelExtractor = null;
	LabelFuser<IT,ET> labelFuser = null;
	CollisionsAwareLabelInsertor<LT,ET> labelInsertor = null;
	LabelPostprocessor<LT> labelCleaner = null;


	protected Vector<Double> inWeights;
	protected double threshold;

	@Override
	public
	void setWeights(final Vector<Double> weights)
	{
		inWeights = weights;
	}

	@Override
	public
	void setThreshold(final double minSumOfWeights)
	{
		threshold = minSumOfWeights;
	}



	/// Flag the "operational mode" regarding labels touching image boundary
	public boolean removeMarkersAtBoundary = false;

	/**
	 * Remove the whole colliding marker if the volume of its colliding portion
	 * is larger than this value. Set to zero (0) if even a single colliding
	 * voxel shall trigger removal of the whole marker.
	 */
	public float removeMarkersCollisionThreshold = 0.1f;

	/**
	 * Flag if original TRA labels should be used for labels for which collision
	 * was detected and the merging process was not able to recover them, or the
	 * marker was not discovered at all.
	 */
	public Boolean insertTRAforCollidingOrMissingMarkers = false;

	public String dbgImgFileName;

	public String reportImageSize(final RandomAccessibleInterval<?> img)
	{ return reportImageSize(img, referenceType.getBitsPerPixel()/8); }
	//
	static
	public String reportImageSize(final RandomAccessibleInterval<?> img, final long pixelInBytes)
	{
		long pixels = 1;
		for (long d : img.dimensionsAsLongArray()) pixels *= d;
		pixels /= 1 << 20;
		return "Size in Mpixels = " + pixels +
		     " ; in MBytes = " + pixels * pixelInBytes;
	}

	@Override
	public
	Img<LT> fuse(final Vector<RandomAccessibleInterval<IT>> inImgs,
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
		log.warn("tmpImg: "+reportImageSize(markerImg));
		log.warn("outImg: "+reportImageSize(markerImg,2));
		log.warn("starting to create images...");
		final Img<ET> tmpImg
			= markerImg.factory().imgFactory(referenceType).create(markerImg);
		log.warn("created tmpImg");

		//create the output image (of the same iteration order as the markerImg),
		//and init it
		final Img<LT> outImg = markerImg.factory().create(markerImg);
		log.warn("created outImg");
		LoopBuilder.setImages(outImg).forEachPixel(SetZero::setZero);
		log.warn("zeroed outImg");

		//aux params for the fusion
		final Vector<RandomAccessibleInterval<IT>> selectedInImgs  = new Vector<>(inWeights.size());
		final Vector<Float>                       selectedInLabels = new Vector<>(inWeights.size());
		log.warn("init A");

		//set to remember already discovered TRA markers
		//(with initial capacity set for 100 markers)
		Set<Integer> mDiscovered = new HashSet<>(100);
		log.warn("init B");

		//init insertion (includes to create (re-usable) insertion status object)
		final LabelInsertor.InsertionStatus insStatus = new LabelInsertor.InsertionStatus();
		log.warn("init C");
		labelInsertor.initialize(outImg);
		log.warn("init D");

		//also prepare the positions holding aux array, and bbox corners
		final long[] minBound = new long[markerImg.numDimensions()];
		final long[] maxBound = new long[markerImg.numDimensions()];

		//sweep over the marker image
		log.warn("starting the main sweep");
		final Cursor<LT> mCursor = markerImg.localizingCursor();
		while (mCursor.hasNext())
		{
			final int curMarker = mCursor.next().getInteger();

			//scan for not yet observed markers (and ignore background values...)
			if ( curMarker > 0 && (!mDiscovered.contains(curMarker)) )
			{
				log.warn("discovered new marker: "+curMarker);
				//found a new marker, determine its size and the AABB it spans
				MajorityOverlapBasedLabelExtractor.findAABB(mCursor, minBound,maxBound);
				log.warn("found its AABB");

				//sweep over all input images
				selectedInImgs.clear();
				selectedInLabels.clear();
				int noOfMatchingImages = 0;
				for (int i = 0; i < inImgs.size(); ++i)
				{
					log.warn("searching input image "+i+" for candidate");
					//find the corresponding label in the input image (in the restricted interval)
					final float matchingLabel = labelExtractor.findMatchingLabel(
							Views.interval(inImgs.get(i), minBound,maxBound),
							Views.interval(markerImg,     minBound,maxBound),
							curMarker);
					log.warn("finished the searching, found "+matchingLabel);

					if (matchingLabel > 0)
					{
						selectedInImgs.add(inImgs.get(i));
						selectedInLabels.add(matchingLabel);
						++noOfMatchingImages;
					}
					else
					{
						selectedInImgs.add(null);
						selectedInLabels.add(0.f);
					}
				}

				if (noOfMatchingImages > 0)
				{
					//reset the temporary image beforehand
					LoopBuilder.setImages(tmpImg).forEachPixel(SetZero::setZero);
					log.warn("zeroed tmpImg");

					//fuse the selected labels into it
					labelFuser.fuseMatchingLabels(selectedInImgs,selectedInLabels,
					                              labelExtractor,inWeights, tmpImg);
					log.warn("fused into tmpImg");

					//save the debug image
					//SimplifiedIO.saveImage(tmpImg, "/Users/ulman/DATA/dbgMerge__"+curMarker+".tif");

					//insert the fused segment into the output image
					labelInsertor.insertLabel(tmpImg, outImg,curMarker, insStatus);
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
		} //after all voxel looping

		//save now a debug image
		if (dbgImgFileName != null && dbgImgFileName.length() > 0)
		{
			SimplifiedIO.saveImage(outImg, dbgImgFileName);
		}

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
				labelCleaner.processLabel(outImg, curMarker);

				//and mark we have processed this marker
				mDiscovered.add(curMarker);
			}
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

		return outImg;
	}
}
