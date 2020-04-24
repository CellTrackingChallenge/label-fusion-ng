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
package de.mpicbg.ulman.fusion;

import net.imglib2.type.numeric.RealType;
import org.scijava.ItemVisibility;
import org.scijava.widget.FileWidget;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.app.StatusService;
import org.scijava.log.LogService;
import org.scijava.ui.UIService;
import org.scijava.command.CommandService;
import org.scijava.command.CommandModule;
import net.imglib2.type.numeric.integer.UnsignedShortType;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import io.scif.img.ImgIOException;
import java.util.List;
import java.util.concurrent.ExecutionException;

import java.util.TreeSet;
import java.text.ParseException;

import javax.swing.BoxLayout;
import javax.swing.JFrame;
import org.jhotdraw.samples.svg.gui.ProgressIndicator;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import java.awt.Button;
import java.awt.Dimension;

import de.mpicbg.ulman.fusion.ng.backbones.WeightedVotingFusionFeeder;
import de.mpicbg.ulman.fusion.ng.backbones.WeightedVotingFusionAlgorithm;
import de.mpicbg.ulman.fusion.ng.BIC;
import de.mpicbg.ulman.fusion.ng.SIMPLE;
import net.celltrackingchallenge.measures.util.NumberSequenceHandler;

@Plugin(type = Command.class, menuPath = "Plugins>Annotations Merging Tool")
public class plugin_GTviaMarkersNG implements Command
{
	@Parameter
	private LogService log;

	@Parameter
	private StatusService statusService;

	@Parameter
	private CommandService commandService;

	@Parameter
	private UIService uiService;


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
			           "SIMPLE"}, //,"STAPLE"},
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

	@Parameter(label = "Threshold:", min = "0.0",
		description = "Pixel is merged if there is more-or-equal to this threshold voters supporting it.")
	private float mergeThreshold=1.0f;

	@Parameter(label = "Timepoints to be processed (e.g. 1-9,23,25):",
		description = "Comma separated list of numbers or intervals, interval is number-hyphen-number.",
		validater = "idxChanged")
	private String fileIdxStr = "0-9";

	@Parameter(label = "Output filename pattern:", style = FileWidget.SAVE_STYLE,
		description = "Please, don't forget to include TTT or TTTT into the filename.",
		callback = "outFileOKAY")
	private File outputPath = new File("CHANGE THIS PATH/mergedTTT.tif");


	//callbacks:
	@SuppressWarnings("unused")
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
		if (mergeModel.startsWith("Threshold - user"))
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
		}
		else
		{
			//STAPLE:
			fileInfoA = " ";
			fileInfoB = "Don't know yet how to use this model.";
			fileInfoC = " ";
			fileInfoD = " ";
		}
	}

	@SuppressWarnings("unused")
	private void idxChanged()
	{
		//check the string is parse-able
		try {
			NumberSequenceHandler.parseSequenceOfNumbers(fileIdxStr,null);
		}
		catch (ParseException e)
		{
			log.warn("Timepoints:\n"+e.getMessage());
			if (!uiService.isHeadless())
				uiService.showDialog("Timepoints:\n"+e.getMessage());
			throw new RuntimeException("Timepoints field is invalid.\n"+e.getMessage());
		}
	}

	//will be also used for sanity checking, thus returns boolean
	private boolean outFileOKAY()
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

	//will be also used for sanity checking, thus returns boolean
	private boolean inFileOKAY()
	{
		//check the job file exists
		if (filePath == null || !filePath.exists())
		{
			log.warn("Job file \""+filePath.getAbsolutePath()+"\" does not exist.");
			statusService.showStatus("Job file \""+filePath.getAbsolutePath()+"\" does not exist.");
			return false;
		}

		//check it has understandable content:
		//is there additional column with weights?
		final boolean weightAvail = mergeModel.startsWith("Threshold - user");

		//read the whole input file
		List<String> job = null;
		try {
			job = Files.readAllLines(Paths.get(filePath.getAbsolutePath()));
		}
		catch (IOException e) {
			log.error("plugin_GTviaMarkers error: "+e);
		}

		int lineNo=0;
		for (String line : job)
		{
			++lineNo;

			//this currently represents the first column/complete line
			String partOne = line;

			//should there be the weight column on this line?
			if (weightAvail && lineNo < job.size())
			{
				//yes, there should be one...
				String[] lineTokens = line.split("\\s+");

				//is there the second column at all?
				if (lineTokens.length == 1)
				{
					log.warn("Missing column with weights on line "+lineNo+".");
					if (!uiService.isHeadless())
					{
						statusService.showStatus("Missing column with weights on line "+lineNo+".");
						uiService.showDialog(    "Missing column with weights on line "+lineNo+".");
					}
					return false;
				}

				//get the first part into the partOne variable
				partOne = new String(); //NB: could be nice to be able to tell the String how much to reserve as we know it
				for (int q=0; q < lineTokens.length-1; ++q)
					partOne += lineTokens[q];

				//is the column actually float-parsable number?
				String partTwo = lineTokens[lineTokens.length-1];
				try {
					Float.parseFloat(partTwo);
				}
				catch (Exception e) {
					log.warn("The weight column \""+partTwo+"\" cannot be parsed as a real number on line "+lineNo+".");
					if (!uiService.isHeadless())
					{
						statusService.showStatus("The weight column \""+partTwo+"\" cannot be parsed as a real number on line "+lineNo+".");
						uiService.showDialog(    "The weight column \""+partTwo+"\" cannot be parsed as a real number on line "+lineNo+".");
					}
					return false;
				}
			}

			//test for presence of the expanding pattern TTT or TTTT
			if (partOne.indexOf("TTT") == -1 || ( (partOne.lastIndexOf("TTT") - partOne.indexOf("TTT")) > 1 ))
			{
				log.warn("Filename \""+partOne+"\" does not contain TTT or TTTT pattern on line "+lineNo+".");
				if (!uiService.isHeadless())
				{
					statusService.showStatus("Filename \""+partOne+"\" does not contain TTT or TTTT pattern on line "+lineNo+".");
					uiService.showDialog(    "Filename \""+partOne+"\" does not contain TTT or TTTT pattern on line "+lineNo+".");
				}
				return false;
			}
		}

		log.info("Job file feels sane.");
		statusService.showStatus("Job file feels sane.");
		return true;
	}

	/** populates Ts in the \e pattern with \e idx, and returns result in a new string,
	    it supports TTT or TTTT */
	String expandFilenamePattern(final String pattern, final int idx)
	{
		//detect position
		int a = pattern.indexOf("TTT");
		int b = pattern.lastIndexOf("TTT");
		//and span
		b = b > a ? 4 : 3;

		String res = pattern.substring(0,a);
		res += String.format(String.format("%c0%dd",'%',b),idx);
		res += pattern.substring(a+b);
		return res;
	}


	//the GUI path entry function:
	@Override
	public void run()
	{
		//check that input file exists,
		//parses it to prepare an array of strings -- a job description,
		//and calls the merging function below -- main()

		//check that input is okay
		if (!inFileOKAY() || !outFileOKAY())
		{
			log.error("plugin_GTviaMarkers error: Input parameters are wrong.");
			if (!uiService.isHeadless())
				uiService.showDialog("There is something wrong with either the job file or output file.");

			return;
		}
		if (!mergeModel.startsWith("Threshold")
		 && !mergeModel.startsWith("Majority")
		 && !mergeModel.startsWith("SIMPLE"))
		{
			log.error("plugin_GTviaMarkers error: Unsupported merging model.");
			if (!uiService.isHeadless())
				uiService.showDialog("plugin_GTviaMarkers error: Unsupported merging model.");

			return;
		}

		//parses job file (which we know is sane for sure) to prepare an array of strings
		//is there additional column with weights?
		final boolean weightAvail = mergeModel.startsWith("Threshold - user");

		//read the whole input file
		List<String> job = null;
		try {
			job = Files.readAllLines(Paths.get(filePath.getAbsolutePath()));
		}
		catch (IOException e) {
			log.error("plugin_GTviaMarkers error: "+e);
		}

		//prepare the output array
		String[] argsPattern = new String[2*job.size()+1]; //= 2*(job.size()-1) +1 +2

		//parse the input job specification file (which we know is sane for sure)
		int lineNo=0;
		for (String line : job)
		{
			//this currently represents the first column/complete line
			String partOne = line;

			//should there be the weight column on this line?
			//are we still on lines where weight column should be handled?
			if (lineNo < (job.size()-1))
			{
				if (weightAvail)
				{
					//yes, there should be one...
					String[] lineTokens = line.split("\\s+");
					//NB: inFileOKAY() was true, so there is the second column

					//get the first part into the partOne variable
					partOne = new String(); //NB: could be nice to be able to tell the String how much to reserve as we know it
					for (int q=0; q < lineTokens.length-1; ++q)
						partOne += lineTokens[q];

					//the weight itself
					argsPattern[2*lineNo +1] = lineTokens[lineTokens.length-1];
				}
				else
				{
					//if user-weights not available, provide own ones
					//(provided we are not parsing the very last line with TRA marker image)
					argsPattern[2*lineNo +1] = "1.0";
				}
			}

			//add the input file item as well
			argsPattern[2*lineNo +0] = partOne;

			++lineNo;
		}

		final float threshold =
			mergeModel.startsWith("Majority") ? (int)((job.size()-1)/2)+1.0f : mergeThreshold;
		argsPattern[2*lineNo -1] = Float.toString(threshold);
		argsPattern[2*lineNo +0] = outputPath.getAbsolutePath();
		//generic job specification is done

		//create an array to hold an "expanded"/instantiated job
		String[] args = new String[argsPattern.length];

		//save the threshold value which is constant all the time
		args[args.length-2] = argsPattern[args.length-2];
		//
		//also weights are constant all the time
		for (int i=1; i < args.length-3; i+=2) args[i] = argsPattern[i];

		//defined here so that finally() block can see them...
		JFrame frame = null;
		Button pbtn = null;
		ProgressIndicator pbar = null;
		ButtonHandler pbtnHandler = null;

		//start up the worker class
		final WeightedVotingFusionAlgorithm<? extends RealType<?>, UnsignedShortType> fuser;
		if (mergeModel.startsWith("SIMPLE"))
		{
			//yield additional SIMPLE-specific parameters
			CommandModule fuserParamsObj;
			try {
				fuserParamsObj = commandService.run(SIMPLE_params.class, true).get();
			} catch (ExecutionException | InterruptedException e) {
				fuserParamsObj = null;
			}

			final SIMPLE fuser_SIMPLE = new SIMPLE(log);

			if (fuserParamsObj != null)
			{
				if (fuserParamsObj.isCanceled())
					throw new RuntimeException("User requested not to run the SIMPLE merger.");

				//forward the parameters values
				fuser_SIMPLE.getFuserReference().maxIters = (int)fuserParamsObj.getInput("maxIters");
				fuser_SIMPLE.getFuserReference().noOfNoUpdateIters = (int)fuserParamsObj.getInput("noOfNoUpdateIters");
				fuser_SIMPLE.getFuserReference().initialQualityThreshold = (double)fuserParamsObj.getInput("initialQualityThreshold");
				fuser_SIMPLE.getFuserReference().stepDownInQualityThreshold = (double)fuserParamsObj.getInput("stepDownInQualityThreshold");
				fuser_SIMPLE.getFuserReference().minimalQualityThreshold = (double)fuserParamsObj.getInput("minimalQualityThreshold");
			}

			log.info("SIMPLE alg params: "+fuser_SIMPLE.getFuserReference().reportSettings());
			fuser = fuser_SIMPLE;
		}
		else
			fuser = new BIC(log);

		final WeightedVotingFusionFeeder<?, UnsignedShortType> feeder
			= new WeightedVotingFusionFeeder(log).setAlgorithm(fuser);

		try {
			//parse out the list of timepoints
			TreeSet<Integer> fileIdxList = new TreeSet<>();
			NumberSequenceHandler.parseSequenceOfNumbers(fileIdxStr,fileIdxList);

			//prepare a progress bar:
			//init the components of the bar
			frame = uiService.isHeadless() ? null : new JFrame("CTC Merging Progress Bar");
			if (frame != null)
			{
				pbar = new ProgressIndicator("Time points processed: ", "",
						0, fileIdxList.size(), false);
				pbtn = new Button("Stop merging");
				pbtnHandler = new ButtonHandler();
				pbtn.setMaximumSize(new Dimension(150, 40));
				pbtn.addActionListener(pbtnHandler);

				//populate the bar and show it
				frame.setLayout(new BoxLayout(frame.getContentPane(), BoxLayout.Y_AXIS));
				frame.add(pbar);
				frame.add(pbtn);
				frame.setMinimumSize(new Dimension(300, 100));
				frame.setLocationByPlatform(true);
				if (uiService.isVisible()) frame.setVisible(true);
			}

			long ttime = System.currentTimeMillis();

			//iterate over all jobs
			int progresCnt = 0;
			for (Integer idx : fileIdxList)
			{
				if (frame != null)
				{
					pbar.setProgress(progresCnt++);
					if (pbtnHandler.buttonPressed()) break;
				}

				//first populate/expand to get a particular instance of a job
				for (int i=0; i < args.length-2; i+=2)
					args[i] = expandFilenamePattern(argsPattern[i],idx);
				args[args.length-1] = expandFilenamePattern(argsPattern[args.length-1],idx);

				log.info("new job:");
				int i=0;
				for (; i < args.length-3; i+=2)
					log.info(i+": "+args[i]+"  "+args[i+1]);
				for (; i < args.length; ++i)
					log.info(i+": "+args[i]);


				long time = System.currentTimeMillis();
				feeder.processJob(args);
				time -= System.currentTimeMillis();
				System.out.println("ELAPSED TIME: "+(-time/1000)+" seconds");
			}

			ttime -= System.currentTimeMillis();
			System.out.println("TOTAL ELAPSED TIME: "+(-ttime/1000)+" seconds");
		}
		catch (UnsupportedOperationException | ImgIOException e) {
			log.error("plugin_GTviaMarkers error: "+e);
		}
		catch (ParseException e)
		{
			if (!uiService.isHeadless())
				uiService.showDialog("Timepoints:\n"+e.getMessage());
		}
		finally {
			//hide away the progress bar once the job is done
			if (frame != null)
			{
				pbtn.removeActionListener(pbtnHandler);
				frame.dispose();
			}
		}
	}


	///a single-purpose, button-event-handler, aux class
	class ButtonHandler implements ActionListener
	{
			  //whitnessed the event already?
			  private boolean buttonPressed = false;

			  @Override
			  public void actionPerformed(ActionEvent e)
			  { buttonPressed = true; }

			  public boolean buttonPressed()
			  { return buttonPressed; }
	}


	/** A no-op plugin existing here only to harvest parameter values for
		the SIMPLE algorithm. The parameters here should be mirrored in
		the SIMPLE public attributes. */
	@Plugin(type = Command.class)
	public static class SIMPLE_params implements Command
	{
		@Parameter
		int maxIters = 4;

		@Parameter
		int noOfNoUpdateIters = 2;

		@Parameter
		double initialQualityThreshold = 0.7;

		@Parameter
		double stepDownInQualityThreshold = 0.1;

		@Parameter
		double minimalQualityThreshold = 0.3;

		@Override
		public void run()
		{ /* intenionally empty */ }
	}
}
