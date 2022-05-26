package de.mpicbg.ulman.fusion.util;

public class DetSegCumulativeScores
{
	public void addCase(double oneJaccardValue) {
		segSum += oneJaccardValue;
		++segCnt;

		sectionSegSum += oneJaccardValue;
		++sectionSegCnt;
	}

	// --------------- global score ---------------
	private double segSum = 0.0;
	private long segCnt = 0;

	public long getNumberOfAllCases() {
		return segCnt;
	}

	public double getOverallScore() {
		return segCnt > 0 ? segSum / (double) segCnt : 0.0;
	}

	// --------------- section score ---------------
	private double sectionSegSum = 0.0;
	private long sectionSegCnt = 0;

	public void startSection() {
		sectionSegSum = 0.0;
		sectionSegCnt = 0;
	}

	public long getNumberOfSectionCases() {
		return sectionSegCnt;
	}

	public double getSectionScore() {
		return sectionSegCnt > 0 ? sectionSegSum / (double) sectionSegCnt : 0.0;
	}
}
