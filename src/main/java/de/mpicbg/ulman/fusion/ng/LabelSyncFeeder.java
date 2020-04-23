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
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.loops.LoopBuilder;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.IntegerType;

import net.imglib2.view.Views;
import org.scijava.log.LogService;
import sc.fiji.simplifiedio.SimplifiedIO;

import java.util.*;

import de.mpicbg.ulman.fusion.ng.extract.MajorityOverlapBasedLabelExtractor;
import de.mpicbg.ulman.fusion.ng.insert.OverwriteLabelInsertor;

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
class LabelSyncFeeder<IT extends RealType<IT>, LT extends IntegerType<LT>>
extends JobIO<IT,LT>
{
	public
	LabelSyncFeeder(final LogService _log)
	{
		this(_log,true);
	}

	public
	LabelSyncFeeder(final LogService _log, final boolean wantPerLabelProcessing)
	{
		super(_log);
		this.wantPerLabelProcessing = wantPerLabelProcessing;
	}

	/** a flag if every output image contains all synced labels, or there is one image per one label */
	public final boolean wantPerLabelProcessing;

	/** supply (and keep updating) the value of the currently processed time point */
	public int currentTime = 0;


	// ----------- output filename pattern -----------
	enum nameFormatTags { time, source, label };

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
	void syncLabels(int... labels)
	{
		syncedLabels = new TreeSet<>();
		for (int l : labels) syncedLabels.add(l);
	}

	public
	void syncLabels(LT... labels)
	{
		syncedLabels = new TreeSet<>();
		for (LT l : labels)  syncedLabels.add( l.getInteger() );
	}

	public
	void syncLabels(final Collection<LT> labels)
	{
		syncedLabels = new TreeSet<>();
		for (LT l : labels)  syncedLabels.add( l.getInteger() );
	}


	// ----------- output regimes -----------
	final OverwriteLabelInsertor<LT,LT> labelInsertor = new OverwriteLabelInsertor<>();

	public
	void syncAllInputsAndSaveAllToDisk(final String... jobSpec)
	{
		saveStreamedImages( syncAllInputsAndStreamIt(jobSpec) );
	}


	public
	ImagesWithOrigin syncAllInputsAndStreamIt(final String... jobSpec)
	{
		//load the image data
		super.processJob(jobSpec);

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
		int currentSource = -1;
		Img<LT> outImg = null;

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
				//still the same source?
				//  yes, add it to the currently built file
				//  no, save this old one and start a new one
				if (currentSource == img.sourceNo)
				{
					//the "yes" branch
					labelInsertor.insertLabel(img.singleLabelImg,outImg,img.markerLabel,null);
				}
				else
				{
					//the "no" branch,
					//but don't save if nothing has been prepared yet...
					if (currentSource != -1)
					{
						final String filename = instantiateFilename(currentSource);
						log.info("Saving file: "+filename);
						SimplifiedIO.saveImage(outImg, filename);
					}

					currentSource = img.sourceNo;
					outImg = img.singleLabelImg.copy();
				}
			}
		}

		if (!wantPerLabelProcessing)
		{
			//save the last image
			final String filename = instantiateFilename(currentSource);
			log.info("Saving file: "+filename);
			SimplifiedIO.saveImage(outImg, filename);
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
		/** the thread in which this::run() is executed */
		private final Thread worker;

		public ImagesWithOrigin()
		{
			//create a new thread that will serve on-demand the images,
			//and that will block/unblock it on this.data
			worker = new Thread(this::run, "label syncer for timepoint "+currentTime);

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

		/** flag set by run() (the 'worker' thread, the label extracting thread)
		    to indicate if it is waiting to process next image */
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
				data.notify(); //unblock run() which does not run until we "release" the 'data'
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


		void run()
		{
		 synchronized (data)
		 {
			log.trace("run() entered");

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

			//create the output image (of the same iteration order as the markerImg),
			data.singleLabelImg = markerImg.factory().create(markerImg);

			//the pixel value that will be (adjusted and then) used to fill the data.singleLabelImg
			final LT markerPixel = data.singleLabelImg.firstElement().createVariable();

			//set to remember already discovered TRA markers
			//(with initial capacity set for 100 markers)
			HashSet<Integer> mDiscovered = new HashSet<>(100);

			//also prepare the positions holding aux array, and bbox corners
			final long[] minBound = new long[markerImg.numDimensions()];
			final long[] maxBound = new long[markerImg.numDimensions()];

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
					//sweep over all input images
					int noOfMatchingImages = 0;
					for (int i = 0; i < inImgs.size(); ++i)
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

								log.trace("run() ready to extract label "+curMarker+" from source "+i);
								//block myself allowing another thread to unblock me when fresh data is desired
								data.wait();
								log.trace("run() extracting label "+curMarker+" from source "+i+" started");

								//init/zero the output image
								LoopBuilder.setImages(data.singleLabelImg).forEachPixel( (a) -> a.setZero() );
								//copy out the label
								markerPixel.setInteger(curMarker);
								labelExtractor.isolateGivenLabel(inImgs.get(i),matchingLabel, data.singleLabelImg,markerPixel);

								data.sourceNo = i;
								data.markerLabel = curMarker;
								log.trace("run() extracting label "+curMarker+" from source "+i+" finished");
								data.notify(); //unblock anyone waiting for the data
							} catch (InterruptedException e) {
								log.trace("run() interrupted");
								return;
							}

							++noOfMatchingImages;
						}
					}

					//some per marker report:
					log.info("TRA marker: "+curMarker+" , images matching: "+noOfMatchingImages);

					//finally, mark we have processed this marker
					mDiscovered.add(curMarker);
				} //after marker processing
			} //after all voxel looping

			waitingForSomeNextProcessing = false;

			log.trace("run() is finished");
		 }
		}
	} // end of public class ImagesWithOrigin
}
