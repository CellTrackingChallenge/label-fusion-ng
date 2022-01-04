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
package de.mpicbg.ulman.fusion.ng.fuse;

import java.util.Vector;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.RealType;
import de.mpicbg.ulman.fusion.ng.extract.LabelExtractor;

/**
 * Fuses selected labels from input images (of voxel type IT -- Input Type)
 * into some other image (of voxel type ET -- Extract_as Type).
 *
 * @param <IT>  voxel type of input images (with labels to be fused together)
 * @param <ET>  voxel type of the output image (into which the input labels are extracted)
 */
public interface LabelFuser<IT extends RealType<IT>, ET extends RealType<ET>>
{
	/**
	 * Fuses selected labels from input images into output image.
	 * The vector of input images may include null pointers.
	 * The values in the output image 'outImg' are not specified, except
	 * that non-zero values are understood to represent the fused segment.
	 *
	 * @param inImgs     image with labels
	 * @param inLabels   what label per image
	 * @param le         how to extract the label
	 * @param inWeights  what weight per image
	 * @param outImg     outcome of the fusion
	 */
	void fuseMatchingLabels(final Vector<RandomAccessibleInterval<IT>> inImgs,
	                        final Vector<Float> inLabels,
	                        final LabelExtractor<IT,?,ET> le,
	                        final Vector<Double> inWeights,
	                        final RandomAccessibleInterval<ET> outImg);
}
