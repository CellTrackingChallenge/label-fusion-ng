/*
 * BSD 2-Clause License
 *
 * Copyright (c) 2023, Vladimír Ulman
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

import de.mpicbg.ulman.fusion.ng.CherryPicker;
import de.mpicbg.ulman.fusion.ng.backbones.JobIO;
import de.mpicbg.ulman.fusion.ng.backbones.WeightedVotingFusionFeeder;
import de.mpicbg.ulman.fusion.util.DetSegCumulativeScores;
import de.mpicbg.ulman.fusion.util.ReusableMemory;
import de.mpicbg.ulman.fusion.util.SegGtImageLoader;
import de.mpicbg.ulman.fusion.util.loggers.SimpleConsoleLogger;
import net.celltrackingchallenge.measures.util.NumberSequenceHandler;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;
import org.scijava.ItemVisibility;
import org.scijava.widget.FileWidget;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.text.ParseException;
import java.util.TreeSet;

@Plugin(type = Command.class, menuPath = "Plugins>Annotations Picker Tool")
public class Picker extends CommonGUI implements Command
{
	// ================= Fiji =================
	@Parameter(visibility = ItemVisibility.MESSAGE, persist = false, required = false)
	private final String headerA =
		"Please, provide a path to a job specification file (see below), and fill required parameters.";

	@Parameter(visibility = ItemVisibility.MESSAGE, persist = false, required = false)
	private final String headerB =
		"Check the status bar (in the main Fiji window) for hint messages.";

	@Parameter(visibility = ItemVisibility.MESSAGE, persist = false, required = false)
	private String fileInfoA = "The job file should list one input filename pattern per line";
	@Parameter(visibility = ItemVisibility.MESSAGE, persist = false, required = false)
	private String fileInfoB = "and space separated single real number weight.";
	@Parameter(visibility = ItemVisibility.MESSAGE, persist = false, required = false)
	private String fileInfoC = "The job file should end with tracking markers filename pattern.";
	@Parameter(visibility = ItemVisibility.MESSAGE, persist = false, required = false)
	private String fileInfoD = "Threshold value is NOT required now.";

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
		final boolean weightAvail = true;
		return JobIO.inFileOKAY(filePath,weightAvail,log,statusService,uiService);
	}

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

	@Parameter(description = "Provide a valid path to the SEG folder for evaluations.")
	String SEGfolder = "CHANGE THIS PATH/dataset/video_GT/SEG";

	@Parameter
	boolean saveFusionResults = true;


	//callbacks:
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

		// ------------ parsing inputs ------------
		final JobSpecification job;
		final TreeSet<Integer> fileIdxList = new TreeSet<>();
		try {
			//should there be an additional column with weights in the job file?
			final boolean weightAvail = true;

			//initiate the building of the job specification...
			JobSpecification.Builder jobSpecsBuilder = JobIO.parseJobFile(filePath.getAbsolutePath(), weightAvail);
			jobSpecsBuilder.setVotingThreshold(0.0);
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
			e.printStackTrace();
			return;
		}

		final SegGtImageLoader<LT> SEGevaluator;
		try {
			SEGevaluator = new SegGtImageLoader<>(SEGfolder, log);
		}
		catch (InvalidPathException e) {
			log.error("SEG GT folder is problematic: "+e.getMessage());
			if (useGui) uiService.showDialog("SEG GT folder is problematic: "+e.getMessage());
			return;
		}

		// ------------ preparing for action ------------
		ReusableMemory.setLogger(log);
		final WeightedVotingFusionFeeder<IT,LT> feeder
				= new WeightedVotingFusionFeeder<IT,LT>(log).setAlgorithm(new CherryPicker<>(log));

		final DetSegCumulativeScores runningDetSegScore = new DetSegCumulativeScores();

		iterateTimePoints(fileIdxList,useGui,time -> {
			job.reportJobForTime(time,log);
			feeder.processJob(job,time, noOfThreads);
			if (saveFusionResults) feeder.saveJob(job,time);
			//
			if (SEGevaluator.managedToLoadImageForTimepoint(time))
			{
				runningDetSegScore.startSection();
				for (final SegGtImageLoader<LT>.LoadedData ld : SEGevaluator.getLastLoadedData())
				{
					ld.calcBoxes();
					feeder.scoreJob_SEG(ld, runningDetSegScore);
				}
				feeder.scoreJob_DET(runningDetSegScore);
				log.info(runningDetSegScore.reportCurrentValues());
			}
		});
		feeder.releaseJobResult();

		log.info("Done, final avg SEG = "+runningDetSegScore.getOverallSegScore()+" obtained over "
				+runningDetSegScore.getNumberOfAllSegCases()+" segments,");
		log.info(" final complete DET = "+runningDetSegScore.getOverallDetScore()+" obtained over "
				+runningDetSegScore.getNumberOfAllDetCases()+" markers");
		log.info("Done picking");
	}


	public static void main(String[] args)
	{
		final Picker myself = new Picker();

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
			System.out.println("The CMV can take form CMV2_8 which enables the CMV partitioning.");
			System.out.println("The SEGfolder is optional param which:");
			System.out.println("  - enables SEG scoring of individual and overall time points,");
			System.out.println("  - disables saving of the output images (because one likely wants");
			System.out.println("    to run again for the full timelapse using the best combination).");
			return;
		}

		myself.filePath = new File(args[0]);
		myself.outputPath = new File(args[2]);
		myself.fileIdxStr = args[3];
		myself.noOfThreads = Integer.parseInt(args[4]);
		myself.SEGfolder = args[5];
		myself.saveFusionResults = false;

		myself.log = new SimpleConsoleLogger();
		myself.worker(false); //false -> run without GUI
	}
}
