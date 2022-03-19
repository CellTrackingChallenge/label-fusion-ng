/*
 * BSD 2-Clause License
 *
 * Copyright (c) 2022, Vladimír Ulman
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
package de.mpicbg.ulman.fusion;

import de.mpicbg.ulman.fusion.util.ReusableMemory;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;

import org.scijava.ItemVisibility;
import org.scijava.command.CommandModule;
import org.scijava.command.CommandService;
import org.scijava.widget.FileWidget;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import org.scijava.log.Logger;
import de.mpicbg.ulman.fusion.util.loggers.SimpleDiskSavingLogger;
import de.mpicbg.ulman.fusion.util.loggers.SimpleRestrictedLogger;

import java.nio.file.InvalidPathException;
import java.util.Map;
import java.util.Vector;
import java.util.List;
import java.util.LinkedList;
import java.util.ArrayList;
import java.util.TreeSet;
import java.text.ParseException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.io.IOException;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import net.celltrackingchallenge.measures.util.NumberSequenceHandler;
import de.mpicbg.ulman.fusion.ng.backbones.JobIO;
import de.mpicbg.ulman.fusion.util.SegGtImageLoader;
import de.mpicbg.ulman.fusion.util.SegGtCumulativeScore;

import de.mpicbg.ulman.fusion.ng.backbones.WeightedVotingFusionFeeder;
import de.mpicbg.ulman.fusion.ng.BIC;
import de.mpicbg.ulman.fusion.ng.BICenhancedFlat;
import de.mpicbg.ulman.fusion.ng.BICenhancedWeighted;
import de.mpicbg.ulman.fusion.ng.SIMPLE;

@Plugin(type = Command.class, menuPath = "Plugins>Annotations Fusing Tools")
public class Fusers extends CommonGUI implements Command
{
	// ================= Fiji =================
	@Parameter(visibility = ItemVisibility.MESSAGE, persist = false, required = false)
	private final String headerA =
		"Please, provide a path to a job specification file (see below), and fill required parameters.";

	@Parameter(visibility = ItemVisibility.MESSAGE, persist = false, required = false)
	private final String headerB =
		"Check the status bar (in the main Fiji window) for hint messages.";

	@Parameter(label = "Merging model:",
			choices = {"Threshold - flat weights",
			           "Threshold - user weights",
			           "Majority - flat weights",
			           "SIMPLE",
			           "BICv2 with FlatVoting, SingleMaskFailSafe and CollisionResolver",
			           "BICv2 with WeightedVoting, SingleMaskFailSafe and CollisionResolver"},
			callback = "mergeModelChangedAndTestJobFile")
	private String mergeModel;

	@Parameter(visibility = ItemVisibility.MESSAGE, persist = false, required = false)
	private String fileInfoA = "The job file should list one input filename pattern per line.";
	@Parameter(visibility = ItemVisibility.MESSAGE, persist = false, required = false)
	private String fileInfoB = "The job file should end with tracking markers filename pattern.";
	@Parameter(visibility = ItemVisibility.MESSAGE, persist = false, required = false)
	private String fileInfoC = " ";
	@Parameter(visibility = ItemVisibility.MESSAGE, persist = false, required = false)
	private String fileInfoD = "Threshold value is required now.";

	@Parameter(visibility = ItemVisibility.MESSAGE, persist = false, required = false)
	private final String fileInfoE =
		 "The filename pattern is a full path to a file that includes TTT or TTTT where "
		+"numbers should be substituted.";

	@Parameter(label = "Job file:", style = FileWidget.OPEN_STYLE,
		description = "Please, make sure that file contains filenames with TTT or TTTT included.",
		callback = "inFileOKAY")
	private File filePath;
	//
	boolean inFileOKAY() {
		//check it has understandable content:
		//is there additional column with weights?
		final boolean weightAvail = mergeModel.startsWith("Threshold - user") ||
		                            mergeModel.startsWith("BICv2");
		return JobIO.inFileOKAY(filePath,weightAvail,log,statusService,uiService);
	}

	@Parameter(label = "Threshold:", min = "0.0",
		description = "Pixel is merged if there is more-or-equal to this threshold voters supporting it.")
	private float mergeThreshold=1.0f;

	@Parameter(label = "Timepoints to be processed (e.g. 1-9,23,25):",
		description = "Comma separated list of numbers or intervals, interval is number-hyphen-number.",
		validater = "idxChanged")
	private String fileIdxStr = "0-9";
	//
	void idxChanged() { super.idxChanged(fileIdxStr); }

	@Parameter(label = "Output filename pattern:", style = FileWidget.SAVE_STYLE,
		description = "Please, don't forget to include TTT or TTTT into the filename.",
		callback = "outFileOKAY")
	private File outputPath = new File("CHANGE THIS PATH/mergedTTT.tif");

	@Parameter(label = "Level of parallelism (no. of threads):", min="1")
	int noOfThreads = 1;

	@Parameter
	boolean doCMV = false;

	@Parameter(description = "Leave empty if you're not interested in doing SEG evaluation of the fusion result.")
	String SEGfolder = "leave empty when unsure";

	@Parameter
	boolean saveFusionResults = true;

	@Parameter
	CommandService commandService;


	//callbacks:
	private void mergeModelChangedAndTestJobFile()
	{
		mergeModelChanged();
		inFileOKAY();
	}
	//
	private void mergeModelChanged()
	{
		if (mergeModel.startsWith("Threshold - flat"))
		{
			fileInfoA = "The job file should list one input filename pattern per line.";
			fileInfoB = "The job file should end with tracking markers filename pattern.";
			fileInfoC = "Threshold value is required now.";
			fileInfoD = " ";
		}
		else
		if (mergeModel.startsWith("Threshold - user") || mergeModel.startsWith("BICv2"))
		{
			fileInfoA = "The job file should list one input filename pattern per line";
			fileInfoB = "and space separated single real number weight.";
			fileInfoC = "The job file should end with tracking markers filename pattern.";
			fileInfoD = "Threshold value is required now.";
		}
		else
		if (mergeModel.startsWith("Majority"))
		{
			fileInfoA = "The job file should list one input filename pattern per line.";
			fileInfoB = "The job file should end with tracking markers filename pattern.";
			fileInfoC = "Threshold value is NOT required now.";
			fileInfoD = " ";
		}
		else
		if (mergeModel.startsWith("SIMPLE"))
		{
			fileInfoA = "The job file should list one input filename pattern per line.";
			fileInfoB = "The job file should end with tracking markers filename pattern.";
			fileInfoC = "Threshold value is NOT required now.";
			fileInfoD = "This model has own configuration dialog.";
		}
		else
		{
			fileInfoA = " ";
			fileInfoB = "Don't know yet how to use this model.";
			fileInfoC = " ";
			fileInfoD = " ";
		}
	}

	//will be also used for sanity checking, thus returns boolean
	boolean outFileOKAY()
	{
		//check the pattern
		final String name = outputPath.getName();
		if (name == null)
		{
			log.warn("No output filename is given.");
			if (statusService != null) //NB: can be also executed in headless...
				statusService.showStatus("No output filename is given.");
			return false;
		}
		//does it contain "TTT" and the number of T's is 3 or 4?
		if (name.indexOf("TTT") == -1 || ( (name.lastIndexOf("TTT") - name.indexOf("TTT")) > 1 ))
		{
			log.warn("Filename \""+name+"\" does not contain TTT or TTTT pattern.");
			if (statusService != null)
				statusService.showStatus("Filename \""+name+"\" does not contain TTT or TTTT pattern.");
			return false;
		}

		//check the parent folder exists
		final File path = outputPath.getParentFile();
		if (path != null && !path.exists())
		{
			log.warn("Parent folder \""+path.getAbsolutePath()+"\" does not exist.");
			if (statusService != null)
				statusService.showStatus("Parent folder \""+path.getAbsolutePath()+"\" does not exist.");
			return false;
		}

		log.info("Filename contains TTT or TTTT pattern, parent folder exists, all good.");
		if (statusService != null)
			statusService.showStatus("Filename contains TTT or TTTT pattern, parent folder exists, all good.");
		return true;
	}


	@Override
	public void run()
	{
		boolean doUseGui = uiService != null && !uiService.isHeadless();
		worker(doUseGui);
	}


	private <IT extends RealType<IT>, LT extends IntegerType<LT>>
	void worker(final boolean useGui)
	{
		// ------------ checks ------------
		if (!inFileOKAY())
		{
			log.error("Input parameters are wrong.");
			if (useGui)
				uiService.showDialog("Stopped because there is something wrong with the job file.");
			return;
		}
		if (!outFileOKAY())
		{
			log.error("Output parameters are wrong.");
			if (useGui)
				uiService.showDialog("There is something wrong with the output file.");
			return;
		}
		if (!mergeModel.startsWith("Threshold")
		 && !mergeModel.startsWith("Majority")
		 && !mergeModel.startsWith("SIMPLE")
		 && !mergeModel.startsWith("BICv2"))
		{
			log.error("Unsupported merging model.");
			if (useGui)
				uiService.showDialog("Unsupported merging model.");
			return;
		}

		// ------------ parsing inputs ------------
		final JobSpecification job;
		final TreeSet<Integer> fileIdxList = new TreeSet<>();
		try {
			//should there be an additional column with weights in the job file?
			final boolean weightAvail = mergeModel.startsWith("Threshold - user") ||
			                            mergeModel.startsWith("BICv2");

			//initiate the building of the job specification...
			JobSpecification.Builder jobSpecsBuilder = JobIO.parseJobFile(filePath.getAbsolutePath(), weightAvail);
			final double threshold =
					mergeModel.startsWith("Majority") ? (jobSpecsBuilder.getNumberOfInputsSoFar()/2)+1 : mergeThreshold;
			jobSpecsBuilder.setVotingThreshold(threshold);
			jobSpecsBuilder.setOutput(outputPath.getAbsolutePath());
			//
			//generic job specification is done
			job = jobSpecsBuilder.build();

			//parse out the list of timepoints
			NumberSequenceHandler.parseSequenceOfNumbers(fileIdxStr,fileIdxList);
		}
		catch (IOException e) {
			log.error("Error parsing job file: "+e.getMessage());
			return;
		} catch (ParseException e) {
			log.error("Error parsing time points: "+e.getMessage());
			if (useGui) uiService.showDialog("Error parsing time points:\n"+e.getMessage());
			return;
		}

		// ------------ preparing for action ------------
		final List<OneCombination<IT,LT>> combinations; //NB: even for non-CMV
		ReusableMemory.setLogger(log);

		if (doCMV) {
			combinations = new LinkedList<>();
			cmv_fillInAllCombinations(job,combinations);

			//prepare output folders
			try {
				for (OneCombination<IT,LT> c : combinations) c.setupOutputFolder(job.outputPattern);
			} catch (IOException e) {
				log.error(e.getMessage());
				throw new RuntimeException("CMV: likely an error with output folders...",e);
			}
		} else {
			combinations = new ArrayList<>(1);
			combinations.add( new OneCombination<>((1 << job.numberOfFusionInputs)-1,job.votingThreshold, job.numberOfFusionInputs) );
		}

		//NB: every combination get its own feeder (and logger and fuser consequently)
		if (mergeModel.startsWith("SIMPLE"))
		{
			//yield additional SIMPLE-specific parameters
			CommandModule fuserParamsObj;
			try {
				fuserParamsObj = commandService.run(SIMPLE_params.class, true).get();
			} catch (ExecutionException | InterruptedException e) {
				fuserParamsObj = null;
			}
			if (fuserParamsObj == null || fuserParamsObj.isCanceled())
				throw new RuntimeException("User requested not to run the SIMPLE fuser.");

			boolean reportOnce = true;
			for (OneCombination<IT,LT> c : combinations)
			{
				final Logger logger = getSubLoggerFrom(log,c);
				final SIMPLE<IT,LT> fuser_SIMPLE = new SIMPLE<>(logger);
				//"forward" the parameters values
				fuser_SIMPLE.getFuserReference().maxIters = (int)fuserParamsObj.getInput("maxIters");
				fuser_SIMPLE.getFuserReference().noOfNoUpdateIters = (int)fuserParamsObj.getInput("noOfNoUpdateIters");
				fuser_SIMPLE.getFuserReference().initialQualityThreshold = (double)fuserParamsObj.getInput("initialQualityThreshold");
				fuser_SIMPLE.getFuserReference().stepDownInQualityThreshold = (double)fuserParamsObj.getInput("stepDownInQualityThreshold");
				fuser_SIMPLE.getFuserReference().minimalQualityThreshold = (double)fuserParamsObj.getInput("minimalQualityThreshold");
				c.feeder = new WeightedVotingFusionFeeder<IT,LT>(logger).setAlgorithm(fuser_SIMPLE);

				if (reportOnce)
				{
					log.info("SIMPLE alg params: "+fuser_SIMPLE.getFuserReference().reportSettings());
					reportOnce = false;
				}
			}
		}
		else
		if (mergeModel.startsWith("BICv2 with Flat"))
		{
			for (OneCombination<IT,LT> c : combinations) {
				final Logger logger = getSubLoggerFrom(log,c);
				c.feeder = new WeightedVotingFusionFeeder<IT,LT>(logger).setAlgorithm(new BICenhancedFlat<>(logger));
			}
		}
		else
		if (mergeModel.startsWith("BICv2 with Weight"))
		{
			for (OneCombination<IT,LT> c : combinations) {
				final Logger logger = getSubLoggerFrom(log,c);
				c.feeder = new WeightedVotingFusionFeeder<IT,LT>(logger).setAlgorithm(new BICenhancedWeighted<>(logger));
			}
		}
		else
		{
			for (OneCombination<IT,LT> c : combinations) {
				final Logger logger = getSubLoggerFrom(log,c);
				c.feeder = new WeightedVotingFusionFeeder<IT,LT>(logger).setAlgorithm(new BIC<>(logger));
			}
		}

		// ------------ action per time point ------------
		final SegGtImageLoader<LT> SEGevaluator;
		try {
			SEGevaluator = SEGfolder.length() > 0 && !SEGfolder.startsWith("leave empty") ?
					new SegGtImageLoader<>(SEGfolder, log) : null;
		}
		catch (InvalidPathException e) {
			log.error("SEG GT folder is problematic: "+e.getMessage());
			if (useGui) uiService.showDialog("SEG GT folder is problematic: "+e.getMessage());
			return;
		}

		if (!doCMV)
		{
			final SegGtCumulativeScore runningSEGscore = new SegGtCumulativeScore();

			//NB: shortcut
			final WeightedVotingFusionFeeder<IT,LT> feeder = combinations.get(0).feeder;
			iterateTimePoints(fileIdxList,useGui,time -> {
				job.reportJobForTime(time,log);
				feeder.processJob(job,time, noOfThreads);
				if (saveFusionResults) feeder.saveJob(job,time);
				//
				if (SEGevaluator != null && SEGevaluator.managedToLoadImageForTimepoint(time))
				{
					SEGevaluator.calcBoxes();
					feeder.scoreJob(SEGevaluator, runningSEGscore);
				}
			});
			feeder.releaseJobResult();

			if (SEGevaluator != null)
				log.info("Done, final avg SEG = "+runningSEGscore.getOverallScore()+" obtained over "
						+runningSEGscore.getNumberOfAllCases()+" segments");
			else
				log.info("Done fusion");
		}
		else
		{
			//CMV LAND HERE!
			//main idea: the last combination is a full one, so we use it to load all images and share them among the rest
			log.info("Doing CMV!   (job's threshold value is thus ignored)");

			//own 'feeder' is already set in every combination; we take now the very last combination
			//(which happens to include all original inputs -- the full job) and make it a
			//'refLoadedImages' for all combinations (including the very last one)
			final OneCombination<IT,LT> fullCombination = combinations.get( combinations.size()-1 );
			for (OneCombination<IT,LT> c : combinations) {
				c.refLoadedImages = fullCombination.feeder;
				//also share the one SEG evaluator among all combination cases
				c.SEGevaluator = SEGevaluator;
			}
			//
			//prevent the 'refLoadedImages' to replace its data with empty initialized content, see OneCombination.call()
			fullCombination.iAmTheRefence = true;

			final ExecutorService cmvers = Executors.newFixedThreadPool(noOfThreads);
			iterateTimePoints(fileIdxList,useGui,time -> {
				try {
					System.gc();
					//NB: hope for some clean up before new round of images loading...

					for (OneCombination<IT,LT> c : combinations) {
						c.currentTime = time;
						job.reportJobForTimeForCombination(time, c, c.feeder.shareLogger());
					}

					//processJob() is loadJob(), calcBoxes() and fuse() (both are inside useAlgorithm())
					log.info("Loading input images for TP="+time);
					long ltime = System.currentTimeMillis();
					fullCombination.feeder.loadJob( job.instantiateForTime(time), cmvers);
					fullCombination.feeder.calcBoxes( cmvers );

					//also pre-load the shared SEG image before the fusion and evaluation
					if (SEGevaluator != null && SEGevaluator.managedToLoadImageForTimepoint(time))
					{
						SEGevaluator.calcBoxes();
						//NB: the status of loading is also available from SEGevaluator.lastLoadedTimepoint == time
						//NB: if loaded now something, scoreJob() is then called later by each combination
					}
					ltime -= System.currentTimeMillis();
					log.info("IMAGES for TP="+time+" LOADING TIME: "+(-ltime/1000)+" seconds");

					//here: all images loaded, boxes possibly computed, therefore...
					//here: ready to start all fusers who start themselves with "stealing" data from the 'fullCombination'

					cmvers.invokeAll(combinations); //calls useAlgorithmWithoutUpdatingBoxes() -> fuse()
					log.info("All combinations for time "+time+" got processed just now.");
				} catch (InterruptedException e) {
					log.error("multithreading error: "+e.getMessage());
					e.printStackTrace();
					throw new RuntimeException("multithreading error",e);
				}
			});
			log.info("Done all fusions, shutting down thread pool..."); //NB: to show/debug the code always got here
			cmvers.shutdownNow();

			if (SEGevaluator != null)
				for (OneCombination<IT,LT> c : combinations) c.reportSEG();
		}
	}


	<IT extends RealType<IT>, LT extends IntegerType<LT>>
	void cmv_fillInAllCombinations(final JobSpecification fullJobLooksLikeThis, final List<OneCombination<IT,LT>> combinations)
	{
		//over all combinations of inputs
		for (int i = 1; i < (1<<fullJobLooksLikeThis.numberOfFusionInputs); ++i)
		{
			//NB: this threshold level is always present
			OneCombination<IT,LT> c = new OneCombination<>(i,1, fullJobLooksLikeThis.numberOfFusionInputs);
			combinations.add(c);

			//over all remaining possible thresholds
			for (int t = 2; t <= c.relevantInputIndices.size(); ++t)
				combinations.add( new OneCombination<>(i,t, fullJobLooksLikeThis.numberOfFusionInputs) );
		}
	}

	String cmv_createFolderName(final OneCombination<?,?> combination, final int numberOfAllPossibleInputs)
	{
		final StringBuilder sb = new StringBuilder();
		for (int i = numberOfAllPossibleInputs-1; i >= 0; --i)
			if (combination.relevantInputIndices.contains(i)) sb.append('Y'); else sb.append('N');
		sb.append('_').append((int)combination.threshold);
		return sb.toString();
	}

	void cmv_printCombinations(final List<OneCombination<?,?>> combinations)
	{
		for (OneCombination<?,?> c : combinations) log.info(c);
	}

	public class OneCombination<IT extends RealType<IT>, LT extends IntegerType<LT>>
	implements Callable<OneCombination<IT,LT>>
	{
		final List<Integer> relevantInputIndices;
		final double threshold;
		public final String code;

		static final int MAXNUMBEROFSUBFOLDERS = 4096;
		final String batchSubFolder;
		public String logFolder = ".";

		OneCombination(final int combinationInDecimal, final double threshold, final int inputsWidth)
		{
			relevantInputIndices = new ArrayList<>(inputsWidth);
			int leftCombinations = combinationInDecimal;
			int bitPos = 0;
			while (leftCombinations > 0)
			{
				int bitMask = 1 << bitPos;
				if ((leftCombinations & bitMask) > 0)
				{
					relevantInputIndices.add(bitPos);
					leftCombinations ^= bitMask;
				}
				++bitPos;
			}

			this.threshold = threshold;
			this.code = cmv_createFolderName(this,inputsWidth);
			this.batchSubFolder = ((1<<inputsWidth)*(inputsWidth/2)) > MAXNUMBEROFSUBFOLDERS ?
					"batch"+(combinationInDecimal / MAXNUMBEROFSUBFOLDERS) : null;
			//NB: flag "subfoldering" if the expected number of combinations exceeds
			//    the max number of subfolders
		}

		@Override
		public String toString()
		{
			StringBuilder sb = new StringBuilder();
			sb.append(code).append(" -> ");
			for (int idx : relevantInputIndices) sb.append(idx).append(',');
			sb.append(';').append(threshold);
			return sb.toString();
		}

		// ----------- processing of the job -----------
		WeightedVotingFusionFeeder<IT,LT> feeder;
		WeightedVotingFusionFeeder<IT,LT> refLoadedImages;
		boolean iAmTheRefence = false;
		SegGtImageLoader<LT> SEGevaluator;
		SegGtCumulativeScore runningSEGscore = new SegGtCumulativeScore();

		private
		void reInitMe()
		{
			feeder.threshold = (float)this.threshold;
			//NB: this is reset even for the reference one because feeder.loadJob()
			//always resets it to the GUI/CLI options which is irrelevant in the CMV mode

			if (iAmTheRefence) return;

			//assumes actual data is already present in 'refLoadedImages',
			//so we cherry-pick from it according to 'releventInputIndices"
			//into 'feeder' and make the feeder do the fusion (in a single thread)

			//first run?
			if (feeder.inImgs == null)
			{
				final int size = relevantInputIndices.size();
				feeder.shareLogger().info("Allocating containers for the shadowed input images of size "+size);

				feeder.inImgs = new Vector<>(size);
				for (int i = 0; i < size; ++i) feeder.inImgs.add(null);

				feeder.inWeights = new Vector<>(size);
				for (int i = 0; i < size; ++i) feeder.inWeights.add(null);

				Vector<Map<Double,long[]>> boxes = new Vector<>(size);
				for (int i = 0; i < size; ++i) boxes.add(null);
				feeder.setInBoxes(boxes);
			}

			Vector<Map<Double,long[]>> refBoxes = refLoadedImages.getInBoxes();
			Vector<Map<Double,long[]>>  myBoxes = feeder.getInBoxes();
			for (int i = 0; i < relevantInputIndices.size(); ++i)
			{
				feeder.inImgs.set(i, refLoadedImages.inImgs.get( relevantInputIndices.get(i) ));
				feeder.inWeights.set(i, refLoadedImages.inWeights.get( relevantInputIndices.get(i) ));
				myBoxes.set(i, refBoxes.get( relevantInputIndices.get(i) ));
			}
			feeder.markerImg = refLoadedImages.markerImg;
			feeder.setMarkerBoxes( refLoadedImages.getMarkerBoxes() );
		}

		@Override
		public OneCombination<IT,LT> call()
		{
			log.info("Combination "+code+" just started fusion");
			long time = System.currentTimeMillis();

			reInitMe();

			feeder.useAlgorithmWithoutUpdatingBoxes();

			if (saveFusionResults)
			{
				log.info("Combination "+code+" just started saving its result");
				feeder.saveJob( JobSpecification.expandFilenamePattern(outputFilenamePattern,currentTime) );
			}

			if (SEGevaluator != null && SEGevaluator.lastLoadedTimepoint == currentTime)
			{
				log.info("Combination "+code+" just started evaluating its result");
				feeder.scoreJob(SEGevaluator, runningSEGscore);
			}

			feeder.releaseJobResult();

			time -= System.currentTimeMillis();
			feeder.shareLogger().info("ELAPSED TIME: "+(-time/1000)+" seconds");
			log.info("Combination "+code+" just finished, after "+(-time/1000)+" seconds");
			return this;
		}

		public void reportSEG()
		{
			feeder.shareLogger().info("Final avg SEG = "+runningSEGscore.getOverallScore()+" obtained over "
					+runningSEGscore.getNumberOfAllCases()+" segments");
		}

		// ----------- saving output images -----------
		String outputFilenamePattern;
		int currentTime;

		void setupOutputFolder(String outputPattern) throws IOException
		{
			//inject this.code before filename
			final int sepPos = outputPattern.lastIndexOf(File.separatorChar);
			String outFolder = (sepPos > -1 ? outputPattern.substring(0,sepPos+1) : "");
			if (batchSubFolder != null)
			{
				makeSureFolderExists(outFolder+ batchSubFolder);
				outFolder += batchSubFolder +File.separatorChar;
			}
			logFolder = outFolder+code;
			makeSureFolderExists(logFolder);

			outputFilenamePattern = logFolder + File.separator
					+ (sepPos > -1 ? outputPattern.substring(sepPos+1) : outputPattern);
		}

		private void makeSureFolderExists(final String folderName) throws IOException
		{
			final Path fPath = Paths.get(folderName);
			if (Files.exists(fPath))
			{
				if (!Files.isDirectory(fPath))
					throw new IOException(folderName+" seems to exist but it is not a directory!");
			} else {
				log.info("Creating output folder: "+folderName);
				Files.createDirectory(fPath);
			}
		}
	}

	private Logger getSubLoggerFrom(final Logger log, final OneCombination<?,?> c)
	{
		if (log instanceof SimpleDiskSavingLogger)
			return ((SimpleDiskSavingLogger)log).subLogger(c);
		//
		return log.subLogger(c.code+" ");
	}


	public static void main(String[] args)
	{
		final Fusers myself = new Fusers();
		myself.mergeModel="BICv2 with FlatVoting, SingleMaskFailSafe and CollisionResolver";
		myself.mergeModelChanged();

		if (args.length != 5 && args.length != 6 && args.length != 7)
		{
			System.out.println("In this regime, it is always using the \"BICv2 with FlatVoting, SingleMaskFailSafe and CollisionResolver\"");
			System.out.println("Usage: pathToJobFile threshold pathToOutputImages timePointsRangeSpecification numberOfThreads [CMV] [SEGfolder]\n");
			System.out.println(myself.fileInfoA);
			System.out.println(myself.fileInfoB);
			System.out.println(myself.fileInfoC);
			System.out.println(myself.fileInfoE);
			System.out.println(myself.fileInfoD);
			System.out.println("timePointsRangeSpecification can be, e.g., 1-9,23,25");
			System.out.println("Set numberOfThreads to 1 to enforce serial (single-threaded) processing.");
			System.out.println("The CMV is optional param which enables the CMV combinatorial search.");
			System.out.println("The SEGfolder is optional param which:");
			System.out.println("  - enables SEG scoring of individual and overall time points,");
			System.out.println("  - disables saving of the output images (because one likely wants");
			System.out.println("    to run again for the full timelapse using the best combination).");
			return;
		}

		myself.doCMV =  args.length >= 6  &&  (args[5].startsWith("cmv") || args[5].startsWith("CMV"));
		myself.log = myself.doCMV ? new SimpleDiskSavingLogger() : new SimpleRestrictedLogger();
		myself.filePath = new File(args[0]);
		myself.mergeThreshold = Float.parseFloat(args[1]);
		myself.outputPath = new File(args[2]);
		myself.fileIdxStr = args[3];
		myself.noOfThreads = Integer.parseInt(args[4]);
		if (args.length == 6 && !myself.doCMV) {
			myself.SEGfolder = args[5];
			myself.saveFusionResults = false;
		}
		else if (args.length == 7) {
			myself.SEGfolder = args[6];
			myself.saveFusionResults = false;
		}

		myself.worker(false); //false -> run without GUI
	}
}
