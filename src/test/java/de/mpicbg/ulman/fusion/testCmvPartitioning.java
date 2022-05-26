package de.mpicbg.ulman.fusion;

public class testCmvPartitioning {
	static void tryParse(final String doCMV_partition, final int noOfInputs) {
		int[] range = {0,0};

		try {
			System.out.print(doCMV_partition+" for "+noOfInputs+" no of inputs (is "+
					(1 << noOfInputs)+" combinations) :  ");
			Fusers.extractFromToCombinationSweepingRange(doCMV_partition, noOfInputs, range);
			System.out.println("PASSED: "+range[0]+" till "+range[1]);
		}
		catch (IllegalArgumentException e) {
			System.out.println("FAILED: "+e.getMessage());
		}
	}


	public static void main(String[] args) {
		tryParse("4", 5);
		tryParse("-3_1_4", 5);
		tryParse("a_1", 5);
		tryParse("1_b", 5);
		tryParse("-3_1", 5);
		tryParse("1_-1", 5);
		tryParse("3_1", 5);
		tryParse("0_1", 5);
		tryParse("1_1", 5);
		tryParse("1_2", 5);
		tryParse("2_2", 5);
		tryParse("3_7", 5);
		tryParse("1_8", 5);
		tryParse("2_8", 5);
		tryParse("3_8", 5);
		tryParse("7_8", 5);
		tryParse("8_8", 5);
		tryParse("1_16", 5);
		tryParse("3_16", 5);
		tryParse("15_16", 5);
		tryParse("16_16", 5);
		tryParse("3_31", 5);
		tryParse("3_32", 5);
		tryParse("3_64", 5);
		tryParse("1_64", 16);
		tryParse("2_64", 16);
		tryParse("63_64", 16);
		tryParse("64_64", 16);
	}
}
