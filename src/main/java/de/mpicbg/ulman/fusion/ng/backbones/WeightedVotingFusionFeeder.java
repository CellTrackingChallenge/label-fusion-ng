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

import de.mpicbg.ulman.fusion.ng.AbstractWeightedVotingRoisFusionAlgorithm;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.IntegerType;
import de.mpicbg.ulman.fusion.JobSpecification;

import org.scijava.log.Logger;
import sc.fiji.simplifiedio.SimplifiedIO;

import java.util.Map;
import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
	WeightedVotingFusionFeeder(final Logger _log)
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
		Img<LT> img = null;
		try { img = useAlgorithm(null); }
		catch (InterruptedException e) { /* cannot happen 'cause no MT */ }
		return img;
	}

	public
	Img<LT> useAlgorithm(final int noOfThreads)
	{
		final ExecutorService w = Executors.newFixedThreadPool(noOfThreads);
		try {
			return useAlgorithm(w);
		} catch (InterruptedException e) {
			throw new RuntimeException("Error in multithreading",e);
		} finally {
			w.shutdownNow();
		}
	}


	public
	Img<LT> useAlgorithm(final ExecutorService threadWorkers)
			throws InterruptedException
	{
		if (algorithm == null)
			throw new RuntimeException("Cannot work without an algorithm.");

		log.info("calling weighted voting algorithm with threshold="+threshold);
		algorithm.setWeights(inWeights);
		algorithm.setThreshold(threshold);
		calcBoxes(threadWorkers);
		return algorithm.fuse(inImgs, markerImg);
	}

	public //NB: because of CMV
	Img<LT> useAlgorithmWithoutUpdatingBoxes()
	{
		if (algorithm == null)
			throw new RuntimeException("Cannot work without an algorithm.");

		log.info("calling weighted voting algorithm with threshold="+threshold);
		algorithm.setWeights(inWeights);
		algorithm.setThreshold(threshold);
		return algorithm.fuse(inImgs, markerImg);
	}


	public //NB: because of CMV
	void calcBoxes(final ExecutorService threadWorkers)
			throws InterruptedException
	{
		if (algorithm instanceof AbstractWeightedVotingRoisFusionAlgorithm)
		{
			AbstractWeightedVotingRoisFusionAlgorithm<IT,LT,?> algRoi
					= (AbstractWeightedVotingRoisFusionAlgorithm<IT,LT,?>)algorithm;
			if (threadWorkers != null)
				algRoi.setupBoxes(inImgs,markerImg,threadWorkers);
			else
				algRoi.setupBoxes(inImgs,markerImg);
			//DEBUG// algRoi.printBoxes();
			log.trace("ROIs (boxes) are ready");
		}
	}

	public //NB: because of CMV
	Map<Double,long[]> getMarkerBoxes()
	{
		if (algorithm instanceof AbstractWeightedVotingRoisFusionAlgorithm)
		{
			AbstractWeightedVotingRoisFusionAlgorithm<IT, LT, ?> algRoi
					= (AbstractWeightedVotingRoisFusionAlgorithm<IT, LT, ?>) algorithm;
			return algRoi.markerBoxes;
		}
		else return null;
	}

	public //NB: because of CMV
	void setMarkerBoxes(final Map<Double,long[]> mBoxes)
	{
		if (algorithm instanceof AbstractWeightedVotingRoisFusionAlgorithm)
		{
			AbstractWeightedVotingRoisFusionAlgorithm<IT, LT, ?> algRoi
					= (AbstractWeightedVotingRoisFusionAlgorithm<IT, LT, ?>) algorithm;
			algRoi.markerBoxes = mBoxes;
		}
	}

	public //NB: because of CMV
	Vector<Map<Double,long[]>> getInBoxes()
	{
		if (algorithm instanceof AbstractWeightedVotingRoisFusionAlgorithm)
		{
			AbstractWeightedVotingRoisFusionAlgorithm<IT, LT, ?> algRoi
					= (AbstractWeightedVotingRoisFusionAlgorithm<IT, LT, ?>) algorithm;
			return algRoi.inBoxes;
		}
		else return null;
	}

	public //NB: because of CMV
	void setInBoxes(final Vector<Map<Double,long[]>> inBoxes)
	{
		if (algorithm instanceof AbstractWeightedVotingRoisFusionAlgorithm)
		{
			AbstractWeightedVotingRoisFusionAlgorithm<IT, LT, ?> algRoi
					= (AbstractWeightedVotingRoisFusionAlgorithm<IT, LT, ?>) algorithm;
			algRoi.inBoxes = inBoxes;
		}
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
		try { processJob(job, time, null); }
		catch (InterruptedException e) { /* cannot happen 'cause no MT */ }
	}

	public
	void processJob(final JobSpecification job, final int time, final int noOfThreads)
	{
		log.info("Processing job with multithreading ("+noOfThreads+" threads)");
		final ExecutorService w = Executors.newFixedThreadPool(noOfThreads);
		try {
			processJob(job,time, w);
		} catch (InterruptedException e) {
			throw new RuntimeException("Error in multithreading",e);
		} finally {
			w.shutdownNow();
		}
	}

	void processJob(final JobSpecification job, final int time, final ExecutorService workerThreads)
			throws InterruptedException
	{
		if (algorithm == null)
			throw new RuntimeException("Cannot work without an algorithm.");
		//NB: expand now... and fail possibly soon before possibly lengthy loading of images
		final String outFile = JobSpecification.expandFilenamePattern(job.outputPattern,time);
		Img<LT> outImg;

		if (workerThreads != null) {
			super.loadJob(job.instantiateForTime(time), workerThreads);
			outImg = useAlgorithm(workerThreads);
		} else {
			super.loadJob(job,time);
			outImg = useAlgorithm(null);
		}

		log.info("Saving file: "+outFile);
		SimplifiedIO.saveImage(outImg, outFile);
	}
}
