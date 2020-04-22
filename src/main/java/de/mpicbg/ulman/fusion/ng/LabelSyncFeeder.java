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

import de.mpicbg.ulman.fusion.ng.insert.OverwriteLabelInsertor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.IntegerType;

import org.scijava.log.LogService;
import sc.fiji.simplifiedio.SimplifiedIO;

import java.util.Enumeration;
import java.util.Vector;

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


	// ----------- output regimes -----------
	final OverwriteLabelInsertor<LT,LT> insertor = new OverwriteLabelInsertor<>();

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
					insertor.insertLabel(img.singleLabelImg,outImg,img.markerLabel,null);
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


		/** one reusable container with the current data,
		    this is the object on which we block/unblock the label syncing progress */
		final ImgWithOrigin data = new ImgWithOrigin();

		/** flag set by run() to indicate if run's thread is waiting to process next image */
		Boolean waitingForSomeNextProcessing = false;


		@Override
		public boolean hasMoreElements()
		{
			synchronized (waitingForSomeNextProcessing)
			{
				log.trace("hasMoreElements() returns status: "+waitingForSomeNextProcessing);
				return waitingForSomeNextProcessing;
			}
		}

		/** starts preparing the next synced image, waits for it and returns it
		    (yes, the image syncing is implementing lazy processing);
		    important note: the method keeps returning the same ImgWithOrigin object
		    whose content is updated with every new call */
		@Override
		public ImgWithOrigin nextElement()
		{
			try
			{
				synchronized (waitingForSomeNextProcessing)
				{
					log.trace("nextElement() entered");
					if (!waitingForSomeNextProcessing)
					{
						log.warn("nextElement(): No data is ready to be synced.");
						return null;
					}

					synchronized (data) { data.notify(); } //unblock run()
					waitingForSomeNextProcessing.wait();   //put myself into waiting queue

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
				}
			} catch (InterruptedException e) {
				e.printStackTrace();
				log.trace("nextElement() is interrupted and returns null");
			}
			return null;
		}

		public void interruptGettingMoreElements()
		{
			worker.interrupt();
		}


		void run()
		{
			log.trace("run() entered");

			//this is simulation for now!
			int counter = 10;
			System.out.println("W: preprocessing images");
			while (counter > 0)
			{
				synchronized (data)
				{
					try {
						//found some new data, signal it and wait until we're told to extract it
						waitingForSomeNextProcessing = true;

						//wait here until we're told to continue syncing
						System.out.println("W: ready to yield fake image #" + counter);
						data.wait();

						//now continue syncing = produce another content for this.data
						System.out.println("W: creating fake image #" + counter);
						Thread.sleep((int)(Math.random()*5000));

						data.singleLabelImg = (Img<LT>) ArrayImgs.unsignedShorts(100, 100);
						data.sourceNo = 5;
						data.markerLabel = counter;

						--counter;
					} catch (InterruptedException e) {
						log.trace("run() interrupted");
						return;
					}

					//new data is hopefully fully extracted, or waiting for it was interrupted;
					//in any case don't hold the called anymore -> notify the caller
					synchronized (waitingForSomeNextProcessing) { waitingForSomeNextProcessing.notify(); }

					//and update the waitingForSomeNextProcessing "here":
					// when "here" is either beginning of this loop or right after it ends
				}
			}
			waitingForSomeNextProcessing = false;

			log.trace("run() is finished");
		}
	}
}
