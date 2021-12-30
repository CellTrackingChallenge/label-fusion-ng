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

import org.scijava.log.Logger;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

public class JobSpecification
{
	public final int numberOfFusionInputs;
	public final List<InputPair> inputs;
	public final double votingThreshold;
	public final String markerPattern;
	public final String outputPattern;

	static public class InputPair
	{
		//... convenience for use cases where weights are not needed
		public InputPair(final String filePathPattern) {
			this(filePathPattern,1.0);
		}
		public InputPair(final String filePathPattern, final double weight) {
			this.filePathPattern = filePathPattern;
			this.weight = weight;
		}
		public final String filePathPattern;
		public final double weight;
	}

	public JobSpecification(final List<InputPair> inputs,
	                        final double votingThreshold,
	                        final String marker,
	                        final String output)
	{
		this.numberOfFusionInputs = inputs.size();
		this.inputs = Collections.unmodifiableList(inputs);
		this.votingThreshold = votingThreshold;
		this.markerPattern = marker;
		this.outputPattern = output;
	}

	// ============= exporting =============
	/** populates Ts in the 'pattern' with 'idx',
	    and returns result in a new string, it supports TTT or TTTT */
	static
	public String expandFilenamePattern(final String pattern, final int idx)
	{
		//detect position... optimistic version, though ;-)
		int b = pattern.lastIndexOf("TTT");
		int a = b;
		while (pattern.charAt(a) == 'T') --a;

		++a;           //'a' is at the first position of the last 'T'-sequence
		b = b+3 - a;   //span is in 'b'

		String res = pattern.substring(0,a);
		res += String.format(String.format("%c0%dd",'%',b),idx);
		res += pattern.substring(a+b);
		return res;
	}

	public String[] createJobArgsForTime(final int timepoint)
	{
		final String[] args = new String[2*numberOfFusionInputs + 3];
		for (int i = 0; i < numberOfFusionInputs; ++i) {
			InputPair p = inputs.get(i);
			args[2*i +0] = expandFilenamePattern(p.filePathPattern, timepoint);
			args[2*i +1] = String.valueOf(p.weight);
		}
		args[2*numberOfFusionInputs +0] = expandFilenamePattern(markerPattern, timepoint);
		args[2*numberOfFusionInputs +1] = String.valueOf(votingThreshold);
		args[2*numberOfFusionInputs +2] = expandFilenamePattern(outputPattern, timepoint);
		return args;
	}

	// ============= printing =============
	public void reportJobArgs(final String[] args, final Logger log)
	{
		log.info("new job:");
		int i=0;
		for (; i < args.length-3; i+=2)
			log.info(i+": "+args[i]+"  "+args[i+1]);
		for (; i < args.length; ++i)
			log.info(i+": "+args[i]);
	}

	public void reportJobForTime(final int timepoint, final Logger log)
	{
		log.info("new job:");
		int i=0;
		for (InputPair p : inputs) {
			log.info(i+": "+expandFilenamePattern(p.filePathPattern,timepoint)+"  "+p.weight);
			++i;
		}
		log.info(i+": "+expandFilenamePattern(markerPattern,timepoint)); ++i;
		log.info(i+": "+votingThreshold); ++i;
		log.info(i+": "+expandFilenamePattern(outputPattern,timepoint));
	}

	// ============= building =============
	static public Builder builder() { return new Builder(); }

	static public class Builder
	{
		private List<InputPair> inputs = new ArrayList<>(20);
		private double votingThreshold = -1.0;
		private String markerPattern = null;
		private String outputPattern = null;

		public void addInput(final String filePathPattern) {
			inputs.add( new InputPair(filePathPattern) );
		}
		public void addInput(final String filePathPattern, final double weight) {
			inputs.add( new InputPair(filePathPattern,weight) );
		}

		public void setMarker(final String markerPattern) {
			this.markerPattern = markerPattern;
		}
		public void setOutput(final String outputPattern) {
			this.outputPattern = outputPattern;
		}
		public void setVotingThreshold(final double votingThreshold) {
			this.votingThreshold = votingThreshold;
		}

		public int getNumberOfInputsSoFar() {
			return inputs.size();
		}

		public JobSpecification build() {
			if (inputs.size() == 0)
				throw new IllegalArgumentException("At least one input filename pattern must be provided.");
			if (votingThreshold < 0)
				throw new IllegalArgumentException("Voting threshold must be provided and non-negative.");
			if (markerPattern == null)
				throw new IllegalArgumentException("(TRA) Marker filename pattern must be provided.");
			if (outputPattern == null)
				throw new IllegalArgumentException("Output filename pattern must be provided.");

			return new JobSpecification(inputs,votingThreshold,markerPattern,outputPattern);
		}
	}
}
