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
package de.mpicbg.ulman.fusion.ng;

import net.imglib2.img.Img;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.RealType;
import java.util.Vector;

/**
 * The minimal interface to fuse together a collection of images showing instance
 * segmentations (with voxels of InputType - IT) where selection of instances is
 * driven by instance detection/segmentation image (with voxels of LabelType - LT).
 *
 * The input instance segmentation images may freely use any set of numbers for
 * their segmentation labels. The marker image should contain exactly one unique
 * label/marker for every object. The segments in the input images that shall
 * correspond to the same object are determined from the amount of overlap of these
 * segments with the relevant marker.
 *
 * The returned output image shall be a fusion of the input segments, and shall
 * carry the labels from the marker image.
 *
 * Implementing classes may use, and typically will be using, additional setter
 * methods to provide beforehand the parameters to the fusion process. Since some
 * of the parameters may relate to the input images, e.g. the weight of an image,
 * the collection holding references on the input images is a one that allows to
 * address images with indices.
 */
public
interface FusionAlgorithm <IT extends RealType<IT>, LT extends RealType<LT>>
{
	/** The workhorse method to fuse 'inImgs' synchronized over the 'markerImg',
	    that is, it fuses together all labels from every image from 'inImgs' that,
	    typically, coincide with a marker label from the 'markerImg'. The marker
	    labels from the 'markerImg' can then be understood that they "choose"
	    relevant labels from the 'inImgs'. */
	Img<LT> fuse(final Vector<RandomAccessibleInterval<IT>> inImgs,
	             final Img<LT> markerImg);
}
