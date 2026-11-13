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
