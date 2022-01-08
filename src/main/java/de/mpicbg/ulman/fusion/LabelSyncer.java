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
import org.scijava.command.Command;
import org.scijava.plugin.Plugin;
import org.scijava.plugin.Parameter;
import org.scijava.widget.FileWidget;

import java.util.TreeSet;
import java.text.ParseException;
import java.io.IOException;
import java.io.File;
import de.mpicbg.ulman.fusion.ng.backbones.JobIO;
import net.celltrackingchallenge.measures.util.NumberSequenceHandler;

import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import de.mpicbg.ulman.fusion.ng.LabelSync;

@Plugin(type = Command.class, name = "LabelSync", menuPath = "Plugins>Annotation Labels Sync")
public class LabelSyncer extends CommonGUI implements Command
{
	// ================= Fiji =================
	@Parameter(visibility = ItemVisibility.MESSAGE, persist = false, required = false)
	private final String headerA =
			"Please, provide a path to a job specification file (see below), and fill required parameters.";

	@Parameter(visibility = ItemVisibility.MESSAGE, persist = false, required = false)
	private final String headerB =
			"Check the status bar (in the main Fiji window) for hint messages.";

	@Parameter(visibility = ItemVisibility.MESSAGE, persist = false, required = false)
	private final String fileInfoA = "The job file should list one input filename pattern per line.";
	@Parameter(visibility = ItemVisibility.MESSAGE, persist = false, required = false)
	private final String fileInfoB = "The job file should end with tracking markers filename pattern.";

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
		return JobIO.inFileOKAY(filePath,false,log,statusService,uiService);
	}

	@Parameter(label = "Timepoints to be processed (e.g. 1-9,23,25):",
		description = "Comma separated list of numbers or intervals, interval is number-hyphen-number.",
		validater = "idxChanged")
	private String fileIdxStr = "0-9";
	//
	void idxChanged() { super.idxChanged(fileIdxStr); }

	@Parameter(visibility = ItemVisibility.MESSAGE, persist = false, required = false)
	private final String fileInfoD = "The output filename needs to include placeholders TTs, SSs and LLs.";

	@Parameter(label = "Output filename pattern:", style = FileWidget.SAVE_STYLE,
		description = "Please, don't forget to include placeholders TT, SS and LL into the filename.",
		callback = "syncOutputFilenameOKAY")
	private File outputPath = new File("CHANGE THIS PATH/syncedTTT_SS_LL.tif");


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
			if (statusService != null) //NB: can be also executed in headless...
				statusService.showStatus("missing some of the letter T or S or L");
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
				if (statusService != null)
					statusService.showStatus("beginning of \"+lbl[i]+\"s is inside \"+lbl[t]+\"s");
				return false;
			}
			if (pos[t] < pos[i+1] && pos[i+1] < pos[t+1])
			{
				log.warn("end of "+lbl[i]+"s is inside "+lbl[t]+"s");
				if (statusService != null)
					statusService.showStatus("end of \"+lbl[i]+\"s is inside \"+lbl[t]+\"s");
				return false;
			}
		}

		//are sequences without interrupts?
		for (int t = 0; t < 5; t += 2) //goes over 0,2,4
		for (int i = pos[t]; i <= pos[t+1]; ++i)
		if (name.charAt(i) != lbl[t])
		{
			log.warn("the sequence of "+lbl[t]+"s is interrupted at character "+i);
			if (statusService != null)
				statusService.showStatus("the sequence of "+lbl[t]+"s is interrupted at character "+i);
			return false;
		}

		return true;
	}
	final private int[]  pos = new int[6];
	final private char[] lbl = new char[] {'T',' ', 'S',' ', 'L'};

	private void updateSyncerOutputFilename(final LabelSync ls, final int p, final int tagPos, final LabelSync.nameFormatTags tagVal)
	{
		ls.outputFilenameFormat = ls.outputFilenameFormat.substring(0,pos[p])
		                        + "%0"+(pos[p+1]-pos[p]+1)+"d"
		                        + ls.outputFilenameFormat.substring(pos[p+1]+1);
		ls.outputFilenameOrder[tagPos] = tagVal;
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
		if (!syncOutputFilenameOKAY())
		{
			log.error("Output parameters are wrong.");
			if (useGui)
				uiService.showDialog("Output filename is not properly formated for labels syncing");
			return;
		}

		// ------------ parsing inputs ------------
		final JobSpecification job;
		final TreeSet<Integer> fileIdxList = new TreeSet<>();
		try {
			JobSpecification.Builder jobSpecsBuilder = JobIO.parseJobFileWithoutWeights(filePath.getAbsolutePath());
			jobSpecsBuilder.setVotingThreshold(0); //NB: irrelevant for this type of job
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
		final LabelSync<? extends RealType<?>, UnsignedShortType> syncer = new LabelSync<>(log);

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

		// ------------ action per time point ------------
		iterateTimePoints(fileIdxList,useGui,time -> {
			job.reportJobForTime(time,log);
			syncer.currentTime = time;
			syncer.loadJob(job,time);
			syncer.syncAllOwnInputsAndSaveAllToDisk();
		});
	}


	// ================= CLI =================
	public static void main(String[] args)
	{
		final LabelSyncer myself = new LabelSyncer();

		//check parameters first
		if (args.length != 3)
		{
			System.out.println("Usage: pathToJobFile pathToOutputImages timePointsRangeSpecification\n");
			System.out.println(myself.fileInfoA);
			System.out.println(myself.fileInfoB);
			System.out.println(myself.fileInfoE);
			System.out.println(myself.fileInfoD);
			System.out.println("timePointsRangeSpecification can be, e.g., 1-9,23,25");
			return;
		}

		myself.log = new CommonGUI.MyLog();
		myself.filePath = new File(args[0]);
		myself.outputPath = new File(args[1]);
		myself.fileIdxStr = args[2];
		myself.worker(false); //false -> run without GUI
	}
}
