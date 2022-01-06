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
package de.mpicbg.ulman.fusion.ng.backbones;

import net.imglib2.img.Img;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.IntegerType;
import de.mpicbg.ulman.fusion.JobSpecification;

import org.scijava.log.LogService;
import sc.fiji.simplifiedio.SimplifiedIO;

/**
 * This class essentially takes care of the IO burden. One provides it with
 * a weighted voting fusion algorithm and a "formatted" job specification
 * as a list of strings:
 *
 * image1_asPathAndFilename, image1_asWeightAsRealNumber,
 * image2_asPathAndFilename, image2_asWeightAsRealNumber,
 * ...
 * imageN_asPathAndFilename, imageN_asWeightAsRealNumber,
 * imageMarker_PathAndFilename, ThresholdAsRealNumber,
 * imageOutput_asPathAndFilename
 *
 * The class then reads the respective images, complements them with
 * extracted weights and the threshold, calls the fusion algorithm,
 * and saves the output image.
 */
public
class WeightedVotingFusionFeeder<IT extends RealType<IT>, LT extends IntegerType<LT>>
extends JobIO<IT,LT>
{
	public
	WeightedVotingFusionFeeder(final LogService _log)
	{
		super(_log);
	}


	public
	WeightedVotingFusionFeeder<IT,LT> setAlgorithm(final WeightedVotingFusionAlgorithm<IT,LT> alg)
	{
		if (alg == null)
			throw new RuntimeException("Please, give me an existing weighted voting algorithm.");

		algorithm = alg;
		return this;
	}

	private WeightedVotingFusionAlgorithm<IT,LT> algorithm;


	public
	Img<LT> useAlgorithm()
	{
		if (algorithm == null)
			throw new RuntimeException("Cannot work without an algorithm.");

		log.info("calling weighted voting algorithm with threshold="+threshold);
		algorithm.setWeights(inWeights);
		algorithm.setThreshold(threshold);
		return algorithm.fuse(inImgs, markerImg);
	}


	public
	void processJob(final String... args)
	{
		if (algorithm == null)
			throw new RuntimeException("Cannot work without an algorithm.");

		super.loadJob(args);
		final Img<LT> outImg = useAlgorithm();

		log.info("Saving file: "+args[args.length-1]);
		SimplifiedIO.saveImage(outImg, args[args.length-1]);
	}


	public
	void processJob(final JobSpecification job, final int time)
	{
		if (algorithm == null)
			throw new RuntimeException("Cannot work without an algorithm.");
		//NB: expand now... and fail possibly soon before possibly lengthy loading of images
		final String outFile = JobSpecification.expandFilenamePattern(job.outputPattern,time);

		super.loadJob(job,time);
		final Img<LT> outImg = useAlgorithm();

		log.info("Saving file: "+outFile);
		SimplifiedIO.saveImage(outImg, outFile);
	}
}
