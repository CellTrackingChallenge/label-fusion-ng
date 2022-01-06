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

import net.imagej.ImgPlus;
import net.imglib2.img.Img;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.IntegerType;
import sc.fiji.simplifiedio.SimplifiedIO;

import org.scijava.app.StatusService;
import org.scijava.ui.UIService;
import org.scijava.log.LogService;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import java.util.List;
import java.util.Vector;

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

	protected final LogService log;

	public
	JobIO(final LogService _log)
	{
		if (_log == null)
			throw new RuntimeException("Please, give me existing LogService.");

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
	    into JobSpecification.Inputs specs and processes it */
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
	    into JobSpecification.Inputs specs and processes it */
	public
	void loadJob(final JobSpecification job, final int time)
	{
		loadJob( job.instantiateForTime(time) );
	}


	/** processes JobSpecification.Inputs job specification */
	public
	void loadJob(final JobSpecification.Inputs jsi)
	{
		log.info("inputs:\n"+jsi); //DEBUG REMOVE VLADO
		final int inputImagesCount = jsi.inputFiles.length;

		//container to store the input images
		inImgs = new Vector<>(inputImagesCount);

		//container to store the input weights
		inWeights = new Vector<>(inputImagesCount);

		//marker image
		markerImg = null;

		//now, try to load the input images
		Img<IT> img = null;
		Object firstImgVoxelType = null;
		String firstImgVoxelTypeString = null;

		//load all of them
		for (int i=0; i < inputImagesCount+1; ++i)
		{
			//load the image
			if (i < inputImagesCount) {
				log.info("Reading pair: " + jsi.inputFiles[i] + " " + jsi.inputWeights[i]);
				img = SimplifiedIO.openImage(jsi.inputFiles[i]);
			} else {
				log.info("Reading marker: " + jsi.markerFile);
				img = SimplifiedIO.openImage(jsi.markerFile);
			}

			//check the type of the image (the combineGTs plug-in requires RealType<>)
			if (!(img.firstElement() instanceof RealType<?>))
				throw new RuntimeException("Input image voxels must be scalars.");

			//check that all input images are of the same type
			//NB: the check excludes the tracking markers image
			if (firstImgVoxelType == null)
			{
				firstImgVoxelType = img.firstElement();
				firstImgVoxelTypeString = firstImgVoxelType.getClass().getSimpleName();
			}
			else if (i < inputImagesCount && !(img.firstElement().getClass().getSimpleName().startsWith(firstImgVoxelTypeString)))
			{
				log.info("first  image  voxel type: "+firstImgVoxelType.getClass().getName());
				log.info("current image voxel type: "+img.firstElement().getClass().getName());
				throw new RuntimeException("Voxel types of all input images must be the same.");
			}

			//check the dimensions, against the first loaded image
			//(if processing second or later image already)
			for (int d=0; i > 0 && d < img.numDimensions(); ++d)
				if (img.dimension(d) != inImgs.get(0).dimension(d))
					throw new RuntimeException((i+1)+"th image has different size in the "
							+d+"th dimension than the first image.");

			//all is fine, add this one into the input list
			if (i < inputImagesCount) inImgs.add(img);
			//or, if loading the last image, remember it as the marker image
			else
			{
				if (!(img.firstElement() instanceof IntegerType<?>))
					throw new RuntimeException("Markers must be stored in an integer-type image, e.g., 8bits or 16bits gray image.");
				markerImg = (Img<LT>)img;
			}

			//also parse and store the weight
			if (i < inputImagesCount)
				inWeights.add( jsi.inputWeights[i] );
		}

		//parse threshold value
		threshold = (float)jsi.threshold;

		//since the simplifiedIO() returns actually always ImgPlus,
		//we better strip away the "plus" extras to make it pure Img<>
		if (markerImg instanceof ImgPlus)
			markerImg = ((ImgPlus<LT>)markerImg).getImg();

		//setup the debug image filename
		/*
		String newName = args[args.length-1];
		final int dotSeparatorIdx = newName.lastIndexOf(".");
		newName = new String(newName.substring(0, dotSeparatorIdx)+"__DBG"+newName.substring(dotSeparatorIdx));
		*/
	}


	// ----------- static helpers for the outter world -----------
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
	                   final LogService log,
	                   final StatusService statusService,
	                   final UIService uiService)
	{
		//check the job file exists
		if (filePath == null) {
			log.warn("The path to a job file was not provided.");
			statusService.showStatus("The path to a job file was not provided.");
			return false;
		}
		if (!filePath.exists()) {
			log.warn("Job file \""+filePath.getAbsolutePath()+"\" does not exist.");
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
						if (!uiService.isHeadless())
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
						if (!uiService.isHeadless())
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
						if (!uiService.isHeadless())
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
				if (!uiService.isHeadless())
				{
					statusService.showStatus("Job file: Filename \""+partOne+"\" does not contain TTT or TTTT pattern on line "+lineNo+".");
					uiService.showDialog(    "Job file: Filename \""+partOne+"\" does not contain TTT or TTTT pattern on line "+lineNo+".");
				}
				return false;
			}
		}

		log.info("Job file feels sane.");
		statusService.showStatus("Job file feels sane.");
		return true;
	}
}
