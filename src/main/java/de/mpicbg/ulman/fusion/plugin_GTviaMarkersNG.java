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
import org.scijava.Context;
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

import de.mpicbg.ulman.fusion.ng.LabelSync;
import de.mpicbg.ulman.fusion.ng.backbones.WeightedVotingFusionFeeder;
import de.mpicbg.ulman.fusion.ng.BIC;
import de.mpicbg.ulman.fusion.ng.BICenhanced;
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
			           "SIMPLE",
			           "BICv2 with FlatVoting, SingleMaskFailSafe and CollisionResolver",
			           "BICv2 with WeightedVoting, SingleMaskFailSafe and CollisionResolver",
			           "Label Syncer"}, //,"STAPLE"},
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
		if (mergeModel.startsWith("Label"))
		{
			fileInfoA = "The job file should list one input filename pattern per line.";
			fileInfoB = "The job file should end with tracking markers filename pattern.";
			fileInfoC = "Threshold value is NOT required now.";
			fileInfoD = "The output filename needs to include placeholders TTs, SSs and LLs.";
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
	private boolean syncOutputFilenameOKAY()
	{
		final String name = outputPath.getAbsolutePath();

		pos[0] = name.indexOf(     lbl[0] );  //begining of Ts
		pos[1] = name.lastIndexOf( lbl[0] );  //end of them
		pos[2] = name.indexOf(     lbl[2] );  //begining of Ss
		pos[3] = name.lastIndexOf( lbl[2] );  //end of them
		pos[4] = name.indexOf(     lbl[4] );  //begining of Ls
		pos[5] = name.lastIndexOf( lbl[4] );  //end of them

		//found every letter?
		if (pos[0] == -1 || pos[2] == -1 || pos[4] == -1)
		{
			log.warn("missing some of the letter T or S or L");
			return false;
		}

		//do they intervine?
		for (int t = 0; t < 5; t += 2) //goes over 0,2,4
		for (int i = 0; i < 5; i += 2)
		{
			//don't test "me against me"
			if (t == i) continue;

			if (pos[t] < pos[i] && pos[i] < pos[t+1])
			{
				log.warn("beginning of "+lbl[i]+"s is inside "+lbl[t]+"s");
				return false;
			}
			if (pos[t] < pos[i+1] && pos[i+1] < pos[t+1])
			{
				log.warn("end of "+lbl[i]+"s is inside "+lbl[t]+"s");
				return false;
			}
		}

		//are sequences without interrupts?
		for (int t = 0; t < 5; t += 2) //goes over 0,2,4
		for (int i = pos[t]; i <= pos[t+1]; ++i)
		if (name.charAt(i) != lbl[t])
		{
			log.warn("the sequence of "+lbl[t]+"s is interrupted at character "+i);
			return false;
		}

		return true;
	}
	final private int[]  pos = new int[6];
	final private char[] lbl = new char[] {'T',' ', 'S',' ', 'L'};

	//will be also used for sanity checking, thus returns boolean
	private boolean outFileOKAY()
	{
		if (mergeModel.startsWith("Label")) return syncOutputFilenameOKAY();

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
		final boolean weightAvail = mergeModel.startsWith("Threshold - user") ||
		                            mergeModel.startsWith("BICv2");

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

	private void updateSyncerOutputFilename(final LabelSync ls, final int p, final int tagPos, final LabelSync.nameFormatTags tagVal)
	{
		ls.outputFilenameFormat = ls.outputFilenameFormat.substring(0,pos[p])
		                        + "%0"+(pos[p+1]-pos[p]+1)+"d"
		                        + ls.outputFilenameFormat.substring(pos[p+1]+1);
		ls.outputFilenameOrder[tagPos] = tagVal;
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
		 && !mergeModel.startsWith("SIMPLE")
		 && !mergeModel.startsWith("BICv2")
		 && !mergeModel.startsWith("Label"))
		{
			log.error("plugin_GTviaMarkers error: Unsupported merging model.");
			if (!uiService.isHeadless())
				uiService.showDialog("plugin_GTviaMarkers error: Unsupported merging model.");

			return;
		}

		//parses job file (which we know is sane for sure) to prepare an array of strings
		//is there additional column with weights?
		final boolean weightAvail = mergeModel.startsWith("Threshold - user") ||
		                            mergeModel.startsWith("BICv2");

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

		//key players (the main worker classes) in this plugin
		final WeightedVotingFusionFeeder<?, UnsignedShortType> feeder;
		final LabelSync<? extends RealType<?>, UnsignedShortType> syncer;

		//start up (some of) the worker class
		if (mergeModel.startsWith("Label"))
		{
			if (!syncOutputFilenameOKAY())
				throw new RuntimeException("Output filename is not formated for Label syncer");

			feeder = null;
			syncer = new LabelSync<>(log);

			//replace the Ts, Ss and Ls patterns with %0Xd
			int minPos = Math.min(pos[0], Math.min(pos[2],pos[4]));
			int maxPos = Math.max(pos[0], Math.max(pos[2],pos[4]));

			//replace from the end
			syncer.outputFilenameFormat = outputPath.getAbsolutePath();
			if (maxPos == pos[0])       updateSyncerOutputFilename(syncer, 0,2,LabelSync.nameFormatTags.time);
			else if (maxPos == pos[2])  updateSyncerOutputFilename(syncer, 2,2,LabelSync.nameFormatTags.source);
			else /* pos[4] */           updateSyncerOutputFilename(syncer, 4,2,LabelSync.nameFormatTags.label);

			//the middle one
			if (minPos != pos[0] && maxPos != pos[0])      updateSyncerOutputFilename(syncer, 0,1,LabelSync.nameFormatTags.time);
			else if (minPos != pos[2] && maxPos != pos[2]) updateSyncerOutputFilename(syncer, 2,1,LabelSync.nameFormatTags.source);
			else /* pos[4] */                              updateSyncerOutputFilename(syncer, 4,1,LabelSync.nameFormatTags.label);

			//the first one
			if (minPos == pos[0])       updateSyncerOutputFilename(syncer, 0,0,LabelSync.nameFormatTags.time);
			else if (minPos == pos[2])  updateSyncerOutputFilename(syncer, 2,0,LabelSync.nameFormatTags.source);
			else /* pos[4] */           updateSyncerOutputFilename(syncer, 4,0,LabelSync.nameFormatTags.label);

			log.trace("output filename format: "+syncer.outputFilenameFormat);
			log.trace("output filename what is at position 1: "+syncer.outputFilenameOrder[0]);
			log.trace("output filename what is at position 2: "+syncer.outputFilenameOrder[1]);
			log.trace("output filename what is at position 3: "+syncer.outputFilenameOrder[2]);

			syncer.wantPerLabelProcessing = true;
			syncer.syncAllLabels();
		}
		else
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
			feeder = new WeightedVotingFusionFeeder(log).setAlgorithm(fuser_SIMPLE);
			syncer = null;
		}
		else
		if (mergeModel.startsWith("BICv2"))
		{
			final BICenhanced bic = new BICenhanced(log);
			bic.setEnforceFlatWeightsVoting( mergeModel.startsWith("BICv2 with FlatVoting") );
			feeder = new WeightedVotingFusionFeeder(log).setAlgorithm(bic);
			syncer = null;
		}
		else
		{
			feeder = new WeightedVotingFusionFeeder(log).setAlgorithm(new BIC(log));
			syncer = null;
		}


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
				if (feeder != null) feeder.processJob(args);
				if (syncer != null)
				{
					syncer.currentTime = idx;
					syncer.syncAllInputsAndSaveAllToDisk(args);
				}
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

	public static void main(String[] args)
	{
		if (args.length != 4)
		{
			System.out.println("Usage: jobFile threshold outputFilesPattern timepoint");
			return;
		}

		final plugin_GTviaMarkersNG worker = new plugin_GTviaMarkersNG();

		final Context ctx = new Context(UIService.class, StatusService.class, LogService.class);
		worker.uiService = ctx.getService(UIService.class);
		worker.log = ctx.getService(LogService.class);
		worker.statusService = ctx.getService(StatusService.class);
		worker.commandService = null;

		worker.mergeModel = "BICv2 with FlatVoting, SingleMaskFailSafe and CollisionResolver";
		//worker.mergeModel = "BICv2 with WeightedVoting, SingleMaskFailSafe and CollisionResolver";
		worker.filePath = new File(args[0]);
		worker.mergeThreshold = Float.parseFloat(args[1]);
		worker.outputPath = new File(args[2]);
		worker.fileIdxStr = args[3];

		worker.run();
		System.out.println("quiting...");
	}
}
