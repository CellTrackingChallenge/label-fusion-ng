/*-
 * #%L
 * label-fusion-ng
 * %%
 * Copyright (C) 2020 - 2026 Vladimír Ulman
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package de.mpicbg.ulman.fusion.util;

public class DetSegCumulativeScores
{
	public void addSegMatch(double oneJaccardValue) {
		segSum += oneJaccardValue;
		++segCnt;

		sectionSegSum += oneJaccardValue;
		++sectionSegCnt;
	}

	public void addSegMiss() {
		addSegMatch(0.0);
	}

	public void addDetFalseNegative() {
		++detFnCnt;
		++sectionDetFnCnt;
	}

	public void addDetFalsePositive() {
		++detFpCnt;
		++sectionDetFpCnt;
	}

	public void addDetTruePositive() {
		++detTpCnt;
		++sectionDetTpCnt;
	}

	// --------------- global score ---------------
	private double segSum = 0.0;
	private long segCnt = 0;
	private long detFnCnt = 0;
	private long detFpCnt = 0;
	private long detTpCnt = 0;

	public String reportCurrentValues() {
		return "segSum "+segSum+" segCnt "+segCnt
				+" detFnCnt "+detFnCnt+" detFpCnt "+detFpCnt+" detTpCnt "+detTpCnt;
	}

	public long getNumberOfAllSegCases() {
		return segCnt;
	}

	public double getOverallSegScore() {
		return segCnt > 0 ? segSum / (double) segCnt : 0.0;
	}

	public long getNumberOfAllDetCases() {
		return detFnCnt+detTpCnt;
	}

	public double getOverallDetScore() {
		double convertingCost = detFnCnt*PENALTY_FN + detFpCnt*PENALTY_FP;
		double buildingCost = getNumberOfAllDetCases()*PENALTY_FN;
		return 1.0 - Math.min(convertingCost,buildingCost)/buildingCost;
	}

	/**
	 * TODO: use directly once it is done better on the CTC measures side;
	 * for now, consider {@link net.celltrackingchallenge.measures.TRA#penalty}
	 */
	static final double PENALTY_FN = 10; //how expensive is to create a new one
	static final double PENALTY_FP = 1;  //how expensive is to remove wrong one

	// --------------- section score ---------------
	private double sectionSegSum = 0.0;
	private long sectionSegCnt = 0;
	private long sectionDetFnCnt = 0;
	private long sectionDetFpCnt = 0;
	private long sectionDetTpCnt = 0;

	public void startSection() {
		sectionSegSum = 0.0;
		sectionSegCnt = 0;
		sectionDetFnCnt = 0;
		sectionDetFpCnt = 0;
		sectionDetTpCnt = 0;
	}

	public long getNumberOfSectionSegCases() {
		return sectionSegCnt;
	}

	public double getSectionSegScore() {
		return sectionSegCnt > 0 ? sectionSegSum / (double) sectionSegCnt : 0.0;
	}

	public long getNumberOfSectionDetCases() {
		return sectionDetFnCnt+sectionDetTpCnt;
	}

	public double getSectionDetScore() {
		double convertingCost = sectionDetFnCnt*PENALTY_FN + sectionDetFpCnt*PENALTY_FP;
		double buildingCost = getNumberOfSectionDetCases()*PENALTY_FN;
		return 1.0 - Math.min(convertingCost,buildingCost)/buildingCost;
	}
}
