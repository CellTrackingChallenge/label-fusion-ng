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
package de.mpicbg.ulman.fusion.ng.backbones;

import cz.it4i.fiji.legacy.ReadFullImage;
import net.imagej.ImgPlus;
import net.imglib2.img.Img;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.IntegerType;
//import sc.fiji.simplifiedio.SimplifiedIO;

import org.scijava.app.StatusService;
import org.scijava.ui.UIService;
import org.scijava.log.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import java.util.List;
import java.util.ArrayList;
import java.util.Vector;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ExecutionException;

import de.mpicbg.ulman.fusion.JobSpecification;

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
 * The class then reads the respective images, complements them with
 * extracted weights and the threshold, and places all of that in their
 * respective attributes.
 */
public
class JobIO<IT extends RealType<IT>, LT extends IntegerType<LT>>
{
	///prevent from creating the class without any connection
	@SuppressWarnings("unused")
	private JobIO()
	{ log = null; } //this is to get rid of some warnings

	protected final Logger log;
	public Logger shareLogger() { return log; }

	public
	JobIO(final Logger _log)
	{
		if (_log == null)
			throw new RuntimeException("Please, give me existing Logger.");

		log = _log;
	}


	// ----------- output attributes with job parameters -----------
	/** output attribute: container to store the input images */
	public Vector<RandomAccessibleInterval<IT>> inImgs;

	/** output attribute: container to store the input weights */
	public Vector<Double> inWeights;

	/** output attribute: marker image */
	public Img<LT> markerImg;

	/** output attribute: threshold value */
	public float threshold;


	// ----------- input job spec to output attributes -----------
	/** converts time-instantiated, String[]-based job specification
	    into JobSpecification.Inputs specs and processes it serially */
	public
	void loadJob(final String... args)
	{
		//check the minimum number of input parameters, should be odd number
		if (args.length < 5 || (args.length&1)==0)
		{
			//print help
			log.info("Usage: img1 weight1 ... imgN weightN TRAimg threshold outImg");
			log.info("All img1 (path to an image file) are TRA marker-wise combined into output outImg.");
			throw new RuntimeException("At least one input image, exactly one marker image and one treshold plus one output image are expected.");
		}
		loadJob( JobSpecification.instanceCopyOf(args) );
	}

	/** processes JobSpecification-based job specification
	    into JobSpecification.Inputs specs and processes it serially */
	public
	void loadJob(final JobSpecification job, final int time)
	{ loadJob( job.instantiateForTime(time) ); }

	/** processes JobSpecification.Inputs job specification
	    into JobSpecification.Inputs specs and processes it serially */
	public
	void loadJob(final JobSpecification.Inputs jsi)
	{
		try { loadJob( jsi, null ); }
		catch (InterruptedException e) { /* cannot happen 'cause no MT */ }
	}

	/** processes JobSpecification-based job specification
	    into JobSpecification.Inputs specs and processes it in parallel */
	public
	void loadJob(final JobSpecification job, final int time, final int noOfThreads)
	{ loadJob( job.instantiateForTime(time), noOfThreads ); }

	/** processes JobSpecification.Inputs job specification
	    into JobSpecification.Inputs specs and processes it in parallel */
	public
	void loadJob(final JobSpecification.Inputs jsi, final int noOfThreads)
	{
		log.info("Loading images with multithreading ("+noOfThreads+" threads)");
		final ExecutorService w = Executors.newFixedThreadPool(noOfThreads);
		try {
			loadJob( jsi, w );
		} catch (InterruptedException e) {
			throw new RuntimeException("Error in multithreading",e);
		} finally {
			w.shutdownNow();
		}
	}


	// ----------- parallelization of loadjob() -----------
	//shared among the image readers
	Object firstImgVoxelType = null;
	String firstImgVoxelTypeString = null;
	long[] firstImgDimensions = null;

	final Object auxSynchronizationToken = true;
	//NB: for synchronized(...), because firstImgVoxelType is null and thus cannot be used

	class LoadOneInput implements Callable<LoadOneInput>
	{
		LoadOneInput(final JobSpecification.Inputs inputBatch, final int whichOneFromTheBatch)
		{
			jsi = inputBatch;
			input_idx = whichOneFromTheBatch;
		}

		//glob data
		final JobSpecification.Inputs jsi;

		//job specific data
		final int input_idx;
		String isErrorMsg; //non-null is flagging an error ;-)

		@Override
		public LoadOneInput call()
		{
			try
			{
				//load the image
				Img<IT> img;
				String reportFileName;
				if (input_idx < jsi.inputFiles.length) {
					reportFileName = jsi.inputFiles[input_idx];
					log.info("Reading pair started: " + reportFileName + " " + jsi.inputWeights[input_idx]);
					//img = SimplifiedIO.openImage(jsi.inputFiles[input_idx]);
					//log.trace("Reading pair done: " + reportFileName + " " + jsi.inputWeights[input_idx]);
					final int channel = input_idx%3; //TODO: intentionally wrong for the test!!!
					img = (Img)ReadFullImage.from("localhost:9080",
							"cf89301e-1ac2-4ad8-85f4-c875396e9d7b",
							jsi.forThisTimePoint, channel,
							0,1,1,1,"0")
							.getImgPlus();
					log.trace("Fetching pair from DataStore at channel: " + channel + " " + jsi.inputWeights[input_idx]);
				} else if (input_idx == jsi.inputFiles.length) {
					reportFileName = jsi.markerFile;
					log.info("Reading marker started: " + reportFileName);
					//img = SimplifiedIO.openImage(jsi.markerFile);
					//log.trace("Reading marker done: " + reportFileName);
					img = (Img)ReadFullImage.from("localhost:9080",
							"cf89301e-1ac2-4ad8-85f4-c875396e9d7b",
							jsi.forThisTimePoint, input_idx,
							0,1,1,1,"0")
							.getImgPlus();
					log.trace("Fetching marker from DataStore at channel: " + input_idx);
				} else {
					//sanity check from "over-parallellism"
					throw new RuntimeException("Can't process input_idx "+input_idx+" when only "
						+jsi.inputFiles.length+"+1 should be loaded");
				}

				//check the type of the image (the combineGTs plug-in requires RealType<>)
				if (!(img.firstElement() instanceof RealType<?>))
					throw new RuntimeException(jsi.inputFiles[input_idx]+" input image voxels must be scalars.");

				//is first image being read? -> define the reference voxel type
				synchronized (auxSynchronizationToken)
				{
					if (firstImgVoxelType == null)
					{
						firstImgVoxelType = img.firstElement();
						firstImgVoxelTypeString = firstImgVoxelType.getClass().getSimpleName();
						//
						firstImgDimensions = new long[img.numDimensions()];
						img.dimensions(firstImgDimensions);
					}
				}
				log.trace("Reading of " + reportFileName + ", 1. test passed");

				//check that all input images are of the same type
				//NB: the check excludes the tracking markers image
				if (input_idx < jsi.inputFiles.length && !(img.firstElement().getClass().getSimpleName().startsWith(firstImgVoxelTypeString)))
				{
					log.error("first  image  voxel type: "+firstImgVoxelType.getClass().getName());
					log.error("current image voxel type: "+img.firstElement().getClass().getName());
					throw new RuntimeException(jsi.inputFiles[input_idx]+" image has different voxel type, all input images must be the same.");
				}

				//check the dimensions, against the first loaded image
				for (int d=0; d < img.numDimensions(); ++d)
					if (img.dimension(d) != firstImgDimensions[d])
						throw new RuntimeException(jsi.inputFiles[input_idx]+" image has different size in the "
								+d+"th dimension than the first image.");
				log.trace("Reading of " + reportFileName + ", 2. tests passed");

				if (input_idx < jsi.inputFiles.length)
				{
					//all is fine, add this one into the input list
					inImgs.set(input_idx,img);
					inWeights.set(input_idx,jsi.inputWeights[input_idx]);
				}
				else
				{
					//or, if loading the last image, remember it as the marker image
					if (!(img.firstElement() instanceof IntegerType<?>))
						throw new RuntimeException("Markers must be stored in an integer-type image, e.g., 8bits or 16bits gray image.");
					markerImg = (Img<LT>)img;
				}
				log.trace("Reading of " + reportFileName + ", assignments passed");
			}
			catch (RuntimeException | IOException e) {
				isErrorMsg = e.getMessage();
			}

			return this;
		} //end of call()
	} //end of class


	/** processes JobSpecification.Inputs job specification
	    into JobSpecification.Inputs specs and processes it in parallel */
	public
	void loadJob(final JobSpecification.Inputs jsi, final ExecutorService workerThreads)
			throws InterruptedException
	{
		final int inputImagesCount = jsi.inputFiles.length;

		//container to store the input images
		inImgs = new Vector<>(inputImagesCount);

		//container to store the input weights
		inWeights = new Vector<>(inputImagesCount);

		//marker image
		markerImg = null;

		//init the shared objects
		firstImgVoxelType = null;
		firstImgVoxelTypeString = null;
		firstImgDimensions = null;

		//"containers" in the vectors need to be created in advance
		//because they are set()'ed in (not add()'ed)
		for (int i = 0; i < inputImagesCount; ++i)
		{
			inImgs.add(null);
			inWeights.add(0.0);
		}

		if (workerThreads == null)
		{
			//singlethreading special case
			for (int i = 0; i < inputImagesCount+1; ++i) //NB: +1 for the marker img
			{
				String errMsg = new LoadOneInput(jsi,i).call().isErrorMsg;
				if (errMsg != null)
					throw new RuntimeException(errMsg);
			}
		}
		else
		{
			//multithreading
			final List<LoadOneInput> tasks = new ArrayList<>(inputImagesCount+1);
			for (int i = 0; i < inputImagesCount+1; ++i) //NB: +1 for the marker img
				tasks.add( new LoadOneInput(jsi,i) );

			//init a workerPool
			try {
				for (Future<LoadOneInput> f : workerThreads.invokeAll(tasks))
				{
					String errMsg = f.get().isErrorMsg;
					if (errMsg != null)
						throw new RuntimeException(errMsg);
				}
			}
			catch (ExecutionException e) {
				log.error("Execution error during multithreading: "+e.getMessage());
				throw new InterruptedException("Interrupting loadJob() because of execution error.");
			}
		}

		//parse threshold value
		threshold = (float)jsi.threshold;

		//since the simplifiedIO() returns actually always ImgPlus,
		//we better strip away the "plus" extras to make it pure Img<>
		if (markerImg instanceof ImgPlus)
			markerImg = ((ImgPlus<LT>)markerImg).getImg();
	}


	// ----------- static helpers for the outer world -----------
	static public
	JobSpecification.Builder parseJobFileWithoutWeights(final String pathToJobFile)
	throws IOException
	{
		return parseJobFile(pathToJobFile,false);
	}

	static public
	JobSpecification.Builder parseJobFile(final String pathToJobFile, final boolean expectWeights)
	throws IOException
	{
		//prepare the output array
		JobSpecification.Builder jobPattern = JobSpecification.builder();

		//read the whole input file
		List<String> job = Files.readAllLines(Paths.get(pathToJobFile));

		//parse the input job specification file (which we know is sane for sure)
		int lineNo=0;
		for (String line : job)
		{
			if (lineNo < (job.size()-1)) {
				//processing input lines

				if (expectWeights) {
					String[] lineTokens = line.split("\\s+");
					//NB: inFileOKAY() was true, so there is the second column

					//get the first part into the filenameColumn variable
					StringBuilder filenameColumn = new StringBuilder();
					for (int q=0; q < lineTokens.length-1; ++q)
						filenameColumn.append( lineTokens[q] );

					//the weight itself
					double weightColumn = Double.parseDouble(lineTokens[lineTokens.length-1]);

					jobPattern.addInput(filenameColumn.toString(),weightColumn);
				} else {
					//user-weights not available -> use the whole line
					jobPattern.addInput(line);
				}
			} else {
				//processing final line with marker filename
				jobPattern.setMarker(line);
			}

			++lineNo;
		}

		return jobPattern;
	}


	//a common callback:
	//will be also used for sanity checking, thus returns boolean
	static public
	boolean inFileOKAY(final File filePath,
	                   final boolean shouldCheckForWeights,
	                   final Logger log,
	                   final StatusService statusService,
	                   final UIService uiService)
	{
		//check the job file exists
		if (filePath == null) {
			log.warn("The path to a job file was not provided.");
			if (uiService != null && !uiService.isHeadless())
				statusService.showStatus("The path to a job file was not provided.");
			return false;
		}
		if (!filePath.exists()) {
			log.warn("Job file \""+filePath.getAbsolutePath()+"\" does not exist.");
			if (uiService != null && !uiService.isHeadless())
				statusService.showStatus("Job file \""+filePath.getAbsolutePath()+"\" does not exist.");
			return false;
		}

		//read the whole input file
		List<String> job = null;
		try {
			job = Files.readAllLines(Paths.get(filePath.getAbsolutePath()));
		}
		catch (IOException e) {
			log.error("Error reading job file: "+e);
			return false;
		}

		int lineNo=0;
		for (String line : job)
		{
			++lineNo;

			//this currently represents the first column/complete line
			String partOne = line;

			//are we still processing lines with input files? ...possibly with weight columns
			if (lineNo < job.size())
			{
				//and if so, should we care about the weight column?
				if (shouldCheckForWeights)
				{
					//yes, there should be one...
					String[] lineTokens = line.split("\\s+");

					//is there the second column at all?
					if (lineTokens.length == 1)
					{
						log.warn("Job file: Missing column with weights on line "+lineNo+".");
						if (uiService != null && !uiService.isHeadless())
						{
							statusService.showStatus("Job file: Missing column with weights on line "+lineNo+".");
							uiService.showDialog(    "Job file: Missing column with weights on line "+lineNo+".");
						}
						return false;
					}

					//get the first part into the partOne variable
					StringBuilder partOneSB = new StringBuilder();
					for (int q=0; q < lineTokens.length-1; ++q)
						partOneSB.append(lineTokens[q]);
					partOne = partOneSB.toString();

					//is the column actually float-parsable number?
					String partTwo = lineTokens[lineTokens.length-1];
					try {
						Float.parseFloat(partTwo);
					}
					catch (Exception e) {
						log.warn("Job file: The weight column \""+partTwo+"\" cannot be parsed as a real number on line "+lineNo+".");
						if (uiService != null && !uiService.isHeadless())
						{
							statusService.showStatus("Job file: The weight column \""+partTwo+"\" cannot be parsed as a real number on line "+lineNo+".");
							uiService.showDialog(    "Job file: The weight column \""+partTwo+"\" cannot be parsed as a real number on line "+lineNo+".");
						}
						return false;
					}
				}
				else
				{
					//no, there shall be no trailing weights...
					//so we test if it does not accidentally end up with numbers..
					String[] lineTokens = line.split("\\s+");
					String possiblyNumber = lineTokens[lineTokens.length-1];
					boolean isNumber = false;
					try {
						Float.parseFloat(possiblyNumber);
						isNumber = true;
					}
					catch (Exception e) { /* intentionally empty */ }
					if (isNumber) {
						log.warn("Job file: There seems to be present the weight column \""+possiblyNumber+"\" on line "+lineNo+".");
						if (uiService != null && !uiService.isHeadless())
						{
							statusService.showStatus("Job file: There seems to be present the weight column \""+possiblyNumber+"\" on line "+lineNo+".");
							uiService.showDialog(    "Job file: There seems to be present the weight column \""+possiblyNumber+"\" on line "+lineNo+".");
						}
					}
				}
			}

			//test for presence of the expanding pattern TTT or TTTT
			if (partOne.indexOf("TTT") == -1 || ( (partOne.lastIndexOf("TTT") - partOne.indexOf("TTT")) > 1 ))
			{
				log.warn("Job file: Filename \""+partOne+"\" does not contain TTT or TTTT pattern on line "+lineNo+".");
				if (uiService != null && !uiService.isHeadless())
				{
					statusService.showStatus("Job file: Filename \""+partOne+"\" does not contain TTT or TTTT pattern on line "+lineNo+".");
					uiService.showDialog(    "Job file: Filename \""+partOne+"\" does not contain TTT or TTTT pattern on line "+lineNo+".");
				}
				return false;
			}
		}

		log.info("Job file feels sane.");
		if (uiService != null && !uiService.isHeadless())
			statusService.showStatus("Job file feels sane.");
		return true;
	}
}
