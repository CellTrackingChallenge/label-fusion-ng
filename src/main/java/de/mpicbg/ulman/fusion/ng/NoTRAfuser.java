/*
 * BSD 2-Clause License
 *
 * Copyright (c) 2020,2022, Vladimír Ulman
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
package de.mpicbg.ulman.fusion.ng;

import de.mpicbg.ulman.fusion.ng.backbones.FusionAlgorithm;
import de.mpicbg.ulman.fusion.ng.postprocess.KeepLargestCCALabelPostprocessor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.planar.PlanarImgFactory;
import net.imglib2.loops.LoopBuilder;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.ByteType;
import org.scijava.log.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

public
class NoTRAfuser<IT extends RealType<IT>>
implements FusionAlgorithm<IT,ByteType>
{
	public
	NoTRAfuser(final Logger _log)
	{
		log = _log;
	}

	private final Logger log;
	private int countThreshold = 1;
	private final static ByteType ONE = new ByteType((byte)1);

	@Override
	public Img<ByteType> fuse(Vector<RandomAccessibleInterval<IT>> inImgs, Img<ByteType> markerImg)
	{
		//since we don't need the marker image, don't touch it at all... it may be null anyway...

		final List<Integer> validIndices = new ArrayList<>(inImgs.size());
		for (int i = 0; i < inImgs.size(); ++i)
			if (inImgs.get(i) != null) validIndices.add(i);

		if (validIndices.isEmpty()) {
			log.warn("Given empty list of input images, not producing anything then.");
			return null;
		}

		Img<ByteType> outImg = new PlanarImgFactory<>(new ByteType()).create(inImgs.get(validIndices.get(0)));
		LoopBuilder.setImages(outImg).forEachPixel(ByteType::setZero);

		int processedImgs = 0;
		final int allImgs = validIndices.size();
		int[] imgsInAction = new int[4];

		while (processedImgs < allImgs) {
			if (allImgs-processedImgs >= 4) {
				imgsInAction[0] = validIndices.get(processedImgs++);
				imgsInAction[1] = validIndices.get(processedImgs++);
				imgsInAction[2] = validIndices.get(processedImgs++);
				imgsInAction[3] = validIndices.get(processedImgs++);
				extractFourLabelsInParallel(imgsInAction,inImgs,outImg);
			}
			else
			if (allImgs-processedImgs >= 3) {
				imgsInAction[0] = validIndices.get(processedImgs++);
				imgsInAction[1] = validIndices.get(processedImgs++);
				imgsInAction[2] = validIndices.get(processedImgs++);
				extractThreeLabelsInParallel(imgsInAction,inImgs,outImg);
			}
			else
			if (allImgs-processedImgs >= 2) {
				imgsInAction[0] = validIndices.get(processedImgs++);
				imgsInAction[1] = validIndices.get(processedImgs++);
				extractTwoLabelsInParallel(imgsInAction,inImgs,outImg);
			}
			else
				LoopBuilder.setImages(inImgs.get(validIndices.get(processedImgs++)),outImg)
						.forEachPixel( (i,o) -> { if (i.getRealFloat() > 0) o.add(ONE); } );
		}

		LoopBuilder.setImages(outImg).forEachPixel( o -> { if (o.getInteger() >= countThreshold) o.setOne(); else o.setZero(); } );

		KeepLargestCCALabelPostprocessor<ByteType> cca = new KeepLargestCCALabelPostprocessor<>();
		cca.processLabel(outImg,1);

		return null;
	}

	void extractTwoLabelsInParallel(int[] indices,
	                                final Vector<RandomAccessibleInterval<IT>> inImgs,
	                                final Img<ByteType> outImg)
	{
		LoopBuilder.setImages(
				inImgs.get(indices[0]),
				inImgs.get(indices[1]),
				outImg)
				.forEachPixel( (a,b,o) -> {
					if (a.getRealFloat() > 0) o.add(ONE);
					if (b.getRealFloat() > 0) o.add(ONE);
				} );
	}

	void extractThreeLabelsInParallel(int[] indices,
	                                  final Vector<RandomAccessibleInterval<IT>> inImgs,
	                                  final Img<ByteType> outImg)
	{
		LoopBuilder.setImages(
				inImgs.get(indices[0]),
				inImgs.get(indices[1]),
				inImgs.get(indices[2]),
				outImg)
				.forEachPixel( (a,b,c,o) -> {
					if (a.getRealFloat() > 0) o.add(ONE);
					if (b.getRealFloat() > 0) o.add(ONE);
					if (c.getRealFloat() > 0) o.add(ONE);
				} );
	}

	void extractFourLabelsInParallel(int[] indices,
	                                 final Vector<RandomAccessibleInterval<IT>> inImgs,
	                                 final Img<ByteType> outImg)
	{
		LoopBuilder.setImages(
				inImgs.get(indices[0]),
				inImgs.get(indices[1]),
				inImgs.get(indices[2]),
				inImgs.get(indices[3]),
				outImg)
		.forEachPixel( (a,b,c,d,o) -> {
			if (a.getRealFloat() > 0) o.add(ONE);
			if (b.getRealFloat() > 0) o.add(ONE);
			if (c.getRealFloat() > 0) o.add(ONE);
			if (d.getRealFloat() > 0) o.add(ONE);
		} );
	}

	public
	void setThreshold(final int minNumberOfVoters)
	{
		countThreshold = minNumberOfVoters;
	}

	public
	int getThreshold()
	{
		return countThreshold;
	}
}
