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
package de.mpicbg.ulman.fusion.ng;

import net.imglib2.Cursor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.loops.LoopBuilder;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.view.Views;

import org.scijava.log.LogService;
import sc.fiji.simplifiedio.SimplifiedIO;
import java.util.*;

import de.mpicbg.ulman.fusion.ng.backbones.JobIO;
import de.mpicbg.ulman.fusion.ng.extract.MajorityOverlapBasedLabelExtractor;

/**
 * This class essentially takes care of the IO burden. One provides it with
 * a "formatted" job specification as a list of strings:
 *
 * image1_asPathAndFilename, image1_asWeightAsRealNumber,
 * image2_asPathAndFilename, image2_asWeightAsRealNumber,
 * ...
 * imageN_asPathAndFilename, imageN_asWeightAsRealNumber,
 * imageMarker_PathAndFilename, ThresholdAsRealNumber,
 * imageOutput_asPathAndFilename
 *
 * The class then reads the respective images, skips over the provided
 * weights and the threshold, calls the label syncing algorithm LabelSyncer,
 * and saves the output image.
 */
public
class LabelSync<IT extends RealType<IT>, LT extends IntegerType<LT>>
extends JobIO<IT,LT>
{
	public LabelSync(final LogService _log)
	{
		super(_log);
	}

	/** a flag if every output image contains all synced labels, or there is one image per one label */
	public boolean wantPerLabelProcessing = true;

	/** supply (and keep updating) the value of the currently processed time point */
	public int currentTime = 0;


	// ----------- output filename pattern -----------
	public enum nameFormatTags { time, source, label }

	public String outputFilenameFormat          = "mask%04d__input%02d__label%d.tif";
	public nameFormatTags[] outputFilenameOrder = { nameFormatTags.time, nameFormatTags.source, nameFormatTags.label };

	/** assumes 'outputFilenameFormat' includes three '%d' references for time, source and label,
	    order of these is given in 'outputFilenameOrder';
	    note that this may raise some formatting exception! */
	String instantiateFilename(final int source, final int label)
	{
		return String.format(outputFilenameFormat,
			getAppropriateValueFor(outputFilenameOrder[0], currentTime,source,label),
			getAppropriateValueFor(outputFilenameOrder[1], currentTime,source,label),
			getAppropriateValueFor(outputFilenameOrder[2], currentTime,source,label)
			);
	}

	/** assumes 'outputFilenameFormat' includes two '%d' references for time and source,
	    order of these is given in 'outputFilenameOrder' and outputFilenameOrder[2] is ignored;
	    note that this may raise some formatting exception! */
	String instantiateFilename(final int source)
	{
		return String.format(outputFilenameFormat,
			getAppropriateValueFor(outputFilenameOrder[0], currentTime,source,-1),
			getAppropriateValueFor(outputFilenameOrder[1], currentTime,source,-1)
			);
	}

	/** given the 'tag' it returns value of either the 'time', 'source' or 'label' */
	int getAppropriateValueFor(final nameFormatTags tag,
	                           final int time, final int source, final int label)
	{
		switch (tag)
		{
			case time:   return time;
			case source: return source;
			case label:  return label;
		}
		//should never get here.... but Java wants some ret value in any case
		return -1;
	}


	// ----------- input regimes: manage label match detection -----------
	final MajorityOverlapBasedLabelExtractor<IT,LT,LT> labelExtractor = new MajorityOverlapBasedLabelExtractor<>();

	public
	void setMinOverlapOverTRA(final float overlapRatio)
	{
		if (overlapRatio < 0 || overlapRatio > 1)
			throw new RuntimeException("Ratio must be between 0 and 1 inclusive.");

		labelExtractor.minFractionOfMarker = overlapRatio;
	}

	public
	float getCurrentMinOverlapOverTRA()
	{
		return labelExtractor.minFractionOfMarker;
	}


	// ----------- input regimes: manage labels of interest -----------
	/** collection of labels to be exported when fuse() is called;
	    setting it to null means to export all labels */
	Collection<Integer> syncedLabels = null;

	public
	void syncAllLabels()
	{
		syncedLabels = null;
	}

	public
	void syncOnlyLabels(int... labels)
	{
		syncedLabels = new TreeSet<>();
		for (int l : labels) syncedLabels.add(l);
	}

	public
	void syncOnlyLabels(LT... labels)
	{
		syncedLabels = new TreeSet<>();
		for (LT l : labels)  syncedLabels.add( l.getInteger() );
	}

	public
	void syncOnlyLabels(final Collection<LT> labels)
	{
		syncedLabels = new TreeSet<>();
		for (LT l : labels)  syncedLabels.add( l.getInteger() );
	}


	// ----------- output regimes -----------
	public
	void syncAllInputsAndSaveAllToDisk(final String... jobSpec)
	{
		saveStreamedImages( syncAllInputsAndStreamIt(jobSpec) );
	}


	public
	ImagesWithOrigin syncAllInputsAndStreamIt(final String... jobSpec)
	{
		//load the image data
		super.loadJob(jobSpec);

		//return the data fetching object
		return new ImagesWithOrigin();
	}


	public
	void syncAllInputsAndSaveAllToDisk(final Vector<RandomAccessibleInterval<IT>> inImgs,
	                                   final Img<LT> markerImg)
	{
		saveStreamedImages( syncAllInputsAndStreamIt(inImgs,markerImg) );
	}

	public
	void syncAllOwnInputsAndSaveAllToDisk()
	{
		saveStreamedImages( syncAllInputsAndStreamIt(this.inImgs,this.markerImg) );
	}


	public
	ImagesWithOrigin syncAllInputsAndStreamIt(final Vector<RandomAccessibleInterval<IT>> inImgs,
	                                          final Img<LT> markerImg)
	{
		//use the provided images instead
		this.inImgs = inImgs;
		this.markerImg = markerImg;

		//return the data fetching object
		return new ImagesWithOrigin();
	}


	// ----------- output helpers -----------
	void saveStreamedImages(final ImagesWithOrigin images)
	{
		while (images.hasMoreElements())
		{
			final ImgWithOrigin img = images.nextElement();

			//pays attention to the this.wantPerLabelProcessing()
			if (wantPerLabelProcessing)
			{
				//save every individual file
				final String filename = instantiateFilename(img.sourceNo, img.markerLabel);
				log.info("Saving file: "+filename);
				SimplifiedIO.saveImage(img.singleLabelImg, filename);
			}
			else
			{
				//save per fully processed image
				final String filename = instantiateFilename(img.sourceNo);
				log.info("Saving file: "+filename);
				SimplifiedIO.saveImage(img.singleLabelImg, filename);
			}
		}
	}


	// ----------- output data provider -----------
	public class ImgWithOrigin
	{
		public Img<LT> singleLabelImg;
		public int sourceNo;
		public int markerLabel;
	}

	public class ImagesWithOrigin
	implements Enumeration<ImgWithOrigin>
	{
		/** the thread in which this::generateSyncedImagesIteratively() is executed */
		private final Thread worker;

		public ImagesWithOrigin()
		{
			markerPixel = markerImg.firstElement().createVariable();

			//create a new thread that will serve on-demand the images,
			//and that will block/unblock it on this.data
			worker = new Thread(this::generateSyncedImagesIteratively, "label syncer for timepoint "+currentTime);

			//start this worker thread, it needs to start crunching the data
			//in order to see what to answer in hasMoreElements()
			worker.start();

			//and give that thread a chance to discover something
			//before we are first asked hasMoreElements()
			try { Thread.sleep(200); } catch (InterruptedException e) {}
		}


		/** one reusable container with the current data, this is the object on which
		    the synchronization between this and the 'worker" thread occurs (so that
		    a caller initiates request for data and waits until it is available) */
		final ImgWithOrigin data = new ImgWithOrigin();

		/** the pixel value that will be (adjusted and then) used to fill the data.singleLabelImg */
		final LT markerPixel;

		/** flag set by generateSyncedImagesIteratively() (the 'worker' thread, the label
		    extracting thread) to indicate if it is waiting to process next image */
		Boolean waitingForSomeNextProcessing = false;


		@Override
		public boolean hasMoreElements()
		{
		 synchronized (data)
		 {
			log.trace("hasMoreElements() returns status: "+waitingForSomeNextProcessing);
			return waitingForSomeNextProcessing;
		 }
		}

		/** starts preparing the next synced image, waits for it and returns it
		    (yes, the image syncing is implementing lazy processing);
		    important note: the method keeps returning the same ImgWithOrigin object
		    whose content is updated with every new call; it however does return
		    null if there's nothing to return (hasMoreElements() would return
		    with false) or the syncing got interrupted */
		@Override
		public ImgWithOrigin nextElement()
		{
		 synchronized (data)
		 {
			try
			{
				log.trace("nextElement() entered");
				if (!waitingForSomeNextProcessing)
				{
					log.warn("nextElement(): No data is ready to be synced.");
					return null;
				}

				log.trace("nextElement() notifies and waits");
				data.notify(); //unblock generateSyncedImagesIteratively() which does not run until we "release" the 'data'
				data.wait();   //block myself "releasing" the 'data'
				log.trace("nextElement() continues");

				if (!worker.isInterrupted())
				{
					log.trace("nextElement() is finished and returns data");
					return data;
				}
				else
				{
					log.trace("nextElement() noticed worker couldn't finish, returns null");
					return null;
				}
			} catch (InterruptedException e) {
				e.printStackTrace();
				log.trace("nextElement() is interrupted and returns null");
			}
			return null;
		 }
		}

		public void interruptGettingMoreElements()
		{
			worker.interrupt();
		}


		void generateSyncedImagesIteratively()
		{
			if (inImgs.size() == 0)
				throw new RuntimeException("Cannot operate on no input images!");

		 synchronized (data)
		 {
			log.trace("generateSyncedImagesIteratively() entered");

			//create the output image (of the same iteration order as the markerImg),
			data.singleLabelImg = markerImg.factory().create(markerImg);

			//set to remember already discovered TRA markers
			//(with initial capacity set for 100 markers)
			final int expectedNoOfTRAmarkers = 300;
			final HashSet<Integer> mDiscovered = new HashSet<>(expectedNoOfTRAmarkers);

			//cache for bbox corners of discovered TRA markers
			final Map<Integer,long[]> minBounds = new HashMap<>(expectedNoOfTRAmarkers);
			final Map<Integer,long[]> maxBounds = new HashMap<>(expectedNoOfTRAmarkers);

			//also prepare the positions holding aux array, and bbox corners
			long[] minBound = new long[markerImg.numDimensions()];
			long[] maxBound = new long[markerImg.numDimensions()];

			//shortcut: the total number of all input images
			final int I = inImgs.size();
			//NB: I >= 1

			stoppedOnThisSource = -1;

			//the run over the first input image is special (it fills the caches)
			//sweep over the marker image
			final Cursor<LT> mCursor = markerImg.localizingCursor();
			while (mCursor.hasNext())
			{
				final int curMarker = mCursor.next().getInteger();

				//scan for not yet observed markers (and ignore background values...),
				//furthermore, if there is a list of wanted ones, check it
				if ( curMarker > 0 && (!mDiscovered.contains(curMarker))
				   && (syncedLabels == null || syncedLabels.contains(curMarker)) )
				{
					//found a new marker, determine its size and the AABB it spans
					MajorityOverlapBasedLabelExtractor.findAABB(mCursor, minBound,maxBound);
/*
					//report detected markers just for debug
					System.out.print("marker "+mCursor.get().getInteger()+": lower corner: (");
					for (int d=0; d < minBound.length-1; ++d)
						System.out.print(minBound[d]+",");
					System.out.println(minBound[minBound.length-1]+")");
					System.out.print("marker "+mCursor.get().getInteger()+": upper corner: (");
					for (int d=0; d < maxBound.length-1; ++d)
						System.out.print(maxBound[d]+",");
					System.out.println(maxBound[maxBound.length-1]+")");
*/
					//does it pay off to fill caches?
					if (I > 1)
					{
						minBounds.put(curMarker,minBound.clone());
						maxBounds.put(curMarker,maxBound.clone());
					}

					//now the first input image
					findAndExtractLabel(curMarker,minBound,maxBound, 0);

					//finally, mark we have processed this marker
					mDiscovered.add(curMarker);
				} //after marker processing
			} //after all voxel looping

			//now over the remaining images, in order; and over all TRA markers
			for (int i = 1; i < I; ++i)
				for (Integer curMarker : minBounds.keySet())
				{
					minBound = minBounds.get(curMarker);
					maxBound = maxBounds.get(curMarker);
					findAndExtractLabel(curMarker,minBound,maxBound, i);
				}

			waitingForSomeNextProcessing = false;
			log.trace("generateSyncedImagesIteratively() is finished");
		 }
		}

		int stoppedOnThisSource;

		private
		void findAndExtractLabel(final int curMarker,
		                         final long[] minBound,
		                         final long[] maxBound,
		                         final int i)
		{
			//find the corresponding label in the input image (in the restricted interval)
			final float matchingLabel = labelExtractor.findMatchingLabel(
					Views.interval(inImgs.get(i), minBound,maxBound),
					Views.interval(markerImg,     minBound,maxBound),
					curMarker);
			//System.out.println(i+". image: found label "+matchingLabel);

			if (matchingLabel > 0)
			{
				try {
					//found some new data, signal it and wait until we're told to extract it
					waitingForSomeNextProcessing = true;

					if (wantPerLabelProcessing || i != stoppedOnThisSource)
					{
						stoppedOnThisSource = i;
						log.trace("generateSyncedImagesIteratively() ready to extract label "+curMarker+" from source "+i+", but now waits");
						//block myself allowing another thread to unblock me when fresh data is desired
						data.wait();
						//unblock anyone waiting for the data, but that code won't run until 'data' is
						//"released" here (which is the end of generateSyncedImagesIteratively() or the data.wait() right above here)
						data.notify();

						//init/zero the output image
						LoopBuilder.setImages(data.singleLabelImg).forEachPixel( (a) -> a.setZero() );
					}

					log.trace("generateSyncedImagesIteratively() extracting label "+curMarker+" from source "+i+" started");
					log.info("TRA marker: "+curMarker+" over source="+i+" is matched with: "+matchingLabel);

					//copy out the label
					markerPixel.setInteger(curMarker);
					labelExtractor.isolateGivenLabel(inImgs.get(i),matchingLabel, data.singleLabelImg,markerPixel);

					//setup the "origin" of the data
					data.sourceNo = i;
					data.markerLabel = wantPerLabelProcessing? curMarker : -1;
					//
					log.trace("generateSyncedImagesIteratively() extracting label "+curMarker+" from source "+i+" finished");

				} catch (InterruptedException e) {
					log.trace("generateSyncedImagesIteratively() interrupted");
				}
			}
		}
	} // end of public class ImagesWithOrigin
}
