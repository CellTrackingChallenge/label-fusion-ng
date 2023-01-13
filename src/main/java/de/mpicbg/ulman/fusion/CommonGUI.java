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

import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.app.StatusService;
import org.scijava.log.LogService;
import org.scijava.ui.UIService;

import java.text.ParseException;
import net.celltrackingchallenge.measures.util.NumberSequenceHandler;

import java.awt.Button;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JFrame;
import javax.swing.BoxLayout;
import org.jhotdraw.samples.svg.gui.ProgressIndicator;

import java.util.Set;
import java.util.function.Consumer;

abstract class CommonGUI
{
	@Parameter
	LogService log;

	@Parameter
	StatusService statusService;

	@Parameter
	UIService uiService;


	//a common callback:
	void idxChanged(final String fileIdxStr)
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


	/** A no-op plugin existing here only to harvest parameter values for
		the SIMPLE algorithm. The parameters here should be mirrored in
		the SIMPLE public attributes. */
	@Plugin(type = Command.class)
	static public class SIMPLE_params implements Command
	{
		@Parameter
		int maxIters = 4;

		@Parameter
		int noOfNoPruneIters = 2;

		@Parameter
		double initialQualityThreshold = 0.7;

		@Parameter
		double stepDownInQualityThreshold = 0.1;

		@Parameter
		double minimalQualityThreshold = 0.3;

		@Override
		public void run()
		{ /* intentionally empty */ }
	}


	///a single-purpose, button-event-handler, aux class
	static class ButtonHandler implements ActionListener
	{
		//witnessed the event already?
		private boolean buttonPressed = false;

		@Override
		public void actionPerformed(ActionEvent e)
		{ buttonPressed = true; }

		public boolean isButtonPressed()
		{ return buttonPressed; }
	}


	void iterateTimePoints(final Set<Integer> timepoints,
	                       final boolean useGui,
	                       final Consumer<Integer> processor)
	{
		//defined here so that finally() block can see them...
		JFrame frame = null;
		Button pbtn = null;
		ProgressIndicator pbar = null;
		ButtonHandler pbtnHandler = null;

		try {
			frame = useGui? new JFrame("CTC Fusion Progress Bar") : null;
			if (frame != null)
			{
				//prepare the progress bar:
				pbar = new ProgressIndicator("Time points processed: ", "",
						0, timepoints.size(), false);
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
			for (Integer idx : timepoints)
			{
				if (frame != null)
				{
					pbar.setProgress(progresCnt++);
					if (pbtnHandler.isButtonPressed()) break;
				}

				long time = System.currentTimeMillis();
				processor.accept(idx);
				time -= System.currentTimeMillis();
				log.info("ELAPSED TIME: "+(-time/1000)+" seconds");
			}

			ttime -= System.currentTimeMillis();
			log.info("TOTAL ELAPSED TIME: "+(-ttime/1000)+" seconds");
		}
		catch (Exception e) {
			log.error("Time point processing error: "+e);
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
}
