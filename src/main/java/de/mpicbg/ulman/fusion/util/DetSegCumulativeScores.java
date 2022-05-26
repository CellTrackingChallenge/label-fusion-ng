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

	// --------------- global score ---------------
	private double segSum = 0.0;
	private long segCnt = 0;

	public long getNumberOfAllSegCases() {
		return segCnt;
	}

	public double getOverallSegScore() {
		return segCnt > 0 ? segSum / (double) segCnt : 0.0;
	}

	// --------------- section score ---------------
	private double sectionSegSum = 0.0;
	private long sectionSegCnt = 0;

	public void startSection() {
		sectionSegSum = 0.0;
		sectionSegCnt = 0;
	}

	public long getNumberOfSectionSegCases() {
		return sectionSegCnt;
	}

	public double getSectionSegScore() {
		return sectionSegCnt > 0 ? sectionSegSum / (double) sectionSegCnt : 0.0;
	}
}
