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

import org.scijava.ItemVisibility;
import org.scijava.command.CommandModule;
import org.scijava.command.CommandService;
import org.scijava.widget.FileWidget;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import java.util.TreeSet;
import java.text.ParseException;
import java.util.concurrent.ExecutionException;
import java.io.IOException;
import java.io.File;
import net.celltrackingchallenge.measures.util.NumberSequenceHandler;
import de.mpicbg.ulman.fusion.ng.backbones.JobIO;

import net.imglib2.type.numeric.integer.UnsignedShortType;
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
			callback = "mergeModelChanged")
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
		                            mergeModel.startsWith("BICv2 with Weight");
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

	@Parameter
	CommandService commandService;


	//callbacks:
	private void mergeModelChanged()
	{
		if (mergeModel.startsWith("Threshold - flat") || mergeModel.startsWith("BICv2 with Flat"))
		{
			fileInfoA = "The job file should list one input filename pattern per line.";
			fileInfoB = "The job file should end with tracking markers filename pattern.";
			fileInfoC = "Threshold value is required now.";
			fileInfoD = " ";
		}
		else
		if (mergeModel.startsWith("Threshold - user") || mergeModel.startsWith("BICv2 with Weight"))
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
		inFileOKAY();
	}

	//will be also used for sanity checking, thus returns boolean
	boolean outFileOKAY()
	{
		//check the pattern
		final String name = outputPath.getName();
		if (name == null)
		{
			log.warn("No output filename is given.");
			statusService.showStatus("No output filename is given.");
			return false;
		}
		//does it contain "TTT" and the number of T's is 3 or 4?
		if (name.indexOf("TTT") == -1 || ( (name.lastIndexOf("TTT") - name.indexOf("TTT")) > 1 ))
		{
			log.warn("Filename \""+name+"\" does not contain TTT or TTTT pattern.");
			statusService.showStatus("Filename \""+name+"\" does not contain TTT or TTTT pattern.");
			return false;
		}

		//check the parent folder exists
		final File path = outputPath.getParentFile();
		if (path != null && !path.exists())
		{
			log.warn("Parent folder \""+path.getAbsolutePath()+"\" does not exist.");
			statusService.showStatus("Parent folder \""+path.getAbsolutePath()+"\" does not exist.");
			return false;
		}

		log.info("Filename contains TTT or TTTT pattern, parent folder exists, all good.");
		statusService.showStatus("Filename contains TTT or TTTT pattern, parent folder exists, all good.");
		return true;
	}


	@Override
	public void run()
	{
		boolean doUseGui = uiService != null && !uiService.isHeadless();
		worker(doUseGui);
	}


	private void worker(final boolean useGui)
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
			                            mergeModel.startsWith("BICv2 with Weight");

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
		final WeightedVotingFusionFeeder<?, UnsignedShortType> feeder;

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

			//"forward" the parameters values
			final SIMPLE fuser_SIMPLE = new SIMPLE(log);
			fuser_SIMPLE.getFuserReference().maxIters = (int)fuserParamsObj.getInput("maxIters");
			fuser_SIMPLE.getFuserReference().noOfNoUpdateIters = (int)fuserParamsObj.getInput("noOfNoUpdateIters");
			fuser_SIMPLE.getFuserReference().initialQualityThreshold = (double)fuserParamsObj.getInput("initialQualityThreshold");
			fuser_SIMPLE.getFuserReference().stepDownInQualityThreshold = (double)fuserParamsObj.getInput("stepDownInQualityThreshold");
			fuser_SIMPLE.getFuserReference().minimalQualityThreshold = (double)fuserParamsObj.getInput("minimalQualityThreshold");

			log.info("SIMPLE alg params: "+fuser_SIMPLE.getFuserReference().reportSettings());
			feeder = new WeightedVotingFusionFeeder(log).setAlgorithm(fuser_SIMPLE);
		}
		else
		if (mergeModel.startsWith("BICv2 with Flat"))
		{
			feeder = new WeightedVotingFusionFeeder(log).setAlgorithm(new BICenhancedFlat(log));
		}
		else
		if (mergeModel.startsWith("BICv2 with Weight"))
		{
			feeder = new WeightedVotingFusionFeeder(log).setAlgorithm(new BICenhancedWeighted(log));
		}
		else
		{
			feeder = new WeightedVotingFusionFeeder(log).setAlgorithm(new BIC(log));
		}

		// ------------ action per time point ------------
		iterateTimePoints(fileIdxList,useGui,time -> {
			job.reportJobForTime(time,log);
			feeder.processJob(job,time);
		});
	}

























	// ==========================================================================================
	public static void main(String[] args)
	{
		if (args.length != 4)
		{
			System.out.println("Usage: jobFile threshold outputFilesPattern timepoint");
			return;
		}
	}
}
