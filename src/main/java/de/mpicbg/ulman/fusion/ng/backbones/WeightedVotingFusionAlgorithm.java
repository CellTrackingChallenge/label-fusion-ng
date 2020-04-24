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
package de.mpicbg.ulman.fusion.ng.backbones;

import net.imglib2.type.numeric.RealType;
import java.util.Vector;

/**
 * Specialized variant of a fusion algorithm that requires weight per every input image,
 * and a threshold to define the minimal necessary cumulation of weights to include a voxel
 * into the fused output. The implementation should follow this pattern:
 *
 * O[x] = SUM_i=indicesOfInputImages w(i)*I(i)[x] >= T ? 1 : 0
 *
 * where
 * O[x] is the indicator (binary) image output value at position 'x',
 * I(i)[x] is the i-th indicator image input value at position 'x',
 * w(i) is the weight associated with the i-th input image, and
 * T is the said minimal necessary cumulation of weights.
 */
public
interface WeightedVotingFusionAlgorithm <IT extends RealType<IT>, LT extends RealType<LT>>
extends FusionAlgorithm<IT,LT>
{
	/** Set weights associated with the input images, needless to say that the length
	    of this collection must match the length of the collection with input images. */
	void setWeights(final Vector<Double> weights);

	/** Sets the minimal necessary cumulation of weights to include a voxel into the output. */
	void setThreshold(final double minSumOfWeights);
}
