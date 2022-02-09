package de.mpicbg.ulman.fusion.util;

public class SegGtCumulativeScore
{
	public void addCase(double oneJaccardValue) {
		sum += oneJaccardValue;
		++cnt;

		sectionSum += oneJaccardValue;
		++sectionCnt;
	}

	// --------------- global score ---------------
	double sum = 0.0;
	long cnt = 0;

	public long getNumberOfAllCases() {
		return cnt;
	}

	public double getOverallScore() {
		return cnt > 0 ? sum / (double)cnt : 0.0;
	}

	// --------------- section score ---------------
	double sectionSum = 0.0;
	long sectionCnt = 0;

	public void startSection() {
		sectionSum = 0.0;
		sectionCnt = 0;
	}

	public long getNumberOfSectionCases() {
		return sectionCnt;
	}

	public double getSectionScore() {
		return sectionCnt > 0 ? sectionSum / (double)sectionCnt : 0.0;
	}
}
