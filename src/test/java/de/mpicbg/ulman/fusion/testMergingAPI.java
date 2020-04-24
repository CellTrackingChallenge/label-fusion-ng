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

import de.mpicbg.ulman.fusion.ng.BIC;
import de.mpicbg.ulman.fusion.ng.SIMPLE;
import de.mpicbg.ulman.fusion.ng.backbones.WeightedVotingFusionAlgorithm;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import org.scijava.Context;
import org.scijava.log.LogService;
import sc.fiji.simplifiedio.SimplifiedIO;
import java.util.Vector;

public class testMergingAPI
{
	//debug params...
	static final boolean saveInputsForInspection = true;
	static final boolean saveOutputsForInspection = true;

	public static void main(final String... args)
	{
		//to demonstrate merging, we need to have:
		// - a TRA markers image                    = 1 image
		// - a collection of instance segmentations = N images
		// - a collection of initial weights        = N weights
		// - a (simple) logger                      = was originally designed for a LogService from scijava
		// - a fusion algorithm                     = a Java object

		//storage for tra markers for their x,y centre coordinates (so array must be twice the number of markers)
		final int segInputsCnt = 3;
		final int traMarkersCnt = 5;
		final int[] centres = new int[2*traMarkersCnt];


		//fake data:
		//the TRA markers image (fills also the 'centres')
		final Img<UnsignedShortType> traImg = createFakeTRA(centres);

		//the collections of input instance segmentations and their weights
		Vector< RandomAccessibleInterval<UnsignedByteType> > segImgs = new Vector<>(segInputsCnt);
		Vector< Double > segWeights = new Vector<>(segInputsCnt);

		for (int i = 0; i < segInputsCnt; ++i)
		{
			//creates a fake cell segments around the tra centres with some "random" shift
			//(so that not all seg inputs are the same)
			segImgs.add( createFakeSegmentation( new int[] {(i*3)%5, (i*4)%5}, centres) );
			segWeights.add( 1.0 );
		}

		if (saveInputsForInspection)
		{
			SimplifiedIO.saveImage(traImg,"tra.tif");
			int cnt = 0;
			for (RandomAccessibleInterval<?> segImg : segImgs)
				SimplifiedIO.saveImage(segImg,"seg"+(++cnt)+".tif");
		}

		//local logger
		final LogService localLogger = new Context(LogService.class).getService(LogService.class);

		//generic fuser (to be defined below)
		WeightedVotingFusionAlgorithm<UnsignedByteType, UnsignedShortType> fuser;

		// ----------------- BIC -----------------
		//fusion params:
		fuser = new BIC<>(localLogger);
		fuser.setThreshold(2);
		fuser.setWeights(segWeights);

		//fusion itself:
		Img<UnsignedShortType> fusedRes = fuser.fuse(segImgs, traImg);

		if (saveOutputsForInspection)
			SimplifiedIO.saveImage(fusedRes,"fused_byBIC.tif");

		// ----------------- SIMPLE -----------------
		final SIMPLE<UnsignedByteType, UnsignedShortType> SIMPLEfuser = new SIMPLE<>(localLogger);
		//_additional_ fusion params:
		SIMPLEfuser.getFuserReference().maxIters=4;
		SIMPLEfuser.getFuserReference().noOfNoUpdateIters=2;
		SIMPLEfuser.getFuserReference().initialQualityThreshold=0.7;
		SIMPLEfuser.getFuserReference().stepDownInQualityThreshold=0.1;
		SIMPLEfuser.getFuserReference().minimalQualityThreshold=0.3;

		//fusion itself (SIMPLE is a generic fuser too):
		fuser = SIMPLEfuser;
		fusedRes = fuser.fuse(segImgs, traImg);

		if (saveOutputsForInspection)
			SimplifiedIO.saveImage(fusedRes,"fused_bySIMPLE.tif");
	}


	//
	// ------------------ only methods to create fake inputs ------------------
	//
	static final int imgWidth  = 150;
	static final int imgHeight = 100;


	public static
	Img<UnsignedByteType> createFakeSegmentation(final int[] shift, final int[] centres)
	{
		if ((centres.length & 1) == 1)
		{
			System.out.println("centres array length must be even!");
			return null;
		}
		if (shift.length < 2)
		{
			System.out.println("shift offset length must be at least 2!");
			return null;
		}

		//segment radius and position
		final double[] radii = {10,10};
		final int[] pos = new int[radii.length];

		//output segmentation image
		Img<UnsignedByteType> segImg = ArrayImgs.unsignedBytes(imgWidth, imgHeight);

		for (int i = 0; i < centres.length; i += 2)
		{
			pos[0] = centres[i+0] + shift[0];
			pos[1] = centres[i+1] + shift[1];
			drawCircle(segImg,++lastUsedSegValue,pos,radii);
		}

		return segImg;
	}

	static float lastUsedSegValue = 0;


	public static
	Img<UnsignedShortType> createFakeTRA(final int[] centres)
	{
		if ((centres.length & 1) == 1)
		{
			System.out.println("centres array length must be even!");
			return null;
		}

		//tra marker radius and position
		final double[] radii = {3,3};
		final int[] pos = new int[radii.length];

		//output image
		Img<UnsignedShortType> traImg = ArrayImgs.unsignedShorts(imgWidth, imgHeight);
		float value = 1;

		for (int i = 0; i < centres.length; i += 2)
		{
			pos[0] = (int)((0.1 + 0.8 * Math.random()) * imgWidth);
			pos[1] = (int)((0.1 + 0.8 * Math.random()) * imgHeight);
			drawCircle(traImg,value,pos,radii);

			value += 1;
			centres[i+0] = pos[0];
			centres[i+1] = pos[1];
		}

		return traImg;
	}


	public static
	<T extends RealType<T>>
	void drawCircle(final Img<T> img, final float value, final int[] atPos, final double[] radii)
	{
		final int N = atPos.length;
		if (N != radii.length)
		{
			System.out.println("pos and radii arrays lengths mismatch!");
			return;
		}

		final double rx2 = radii[0]*radii[0];
		final double ry2 = radii[1]*radii[1];

		final RandomAccess<T> ra = img.randomAccess();

		for (int dy = (int)Math.floor(-radii[1]); dy <= (int)Math.ceil(radii[1]); ++dy)
		for (int dx = (int)Math.floor(-radii[0]); dx <= (int)Math.ceil(radii[0]); ++dx)
		{
			if ((dx*dx/rx2 + dy*dy/ry2) <= 1.0                           //inside the ellipse?
			  && (atPos[0]+dx) > 0 && (atPos[0]+dx) < img.dimension(0)   //inside the image?
			  && (atPos[1]+dy) > 0 && (atPos[1]+dy) < img.dimension(1))
			{
				//yes to all! draw it...
				ra.setPosition(atPos[0]+dx, 0);
				ra.setPosition(atPos[1]+dy, 1);
				ra.get().setReal(value);
			}
		}
	}
}
