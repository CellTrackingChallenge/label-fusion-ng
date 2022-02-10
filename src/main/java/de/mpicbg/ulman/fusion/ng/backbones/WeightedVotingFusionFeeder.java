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
import de.mpicbg.ulman.fusion.ng.extract.MajorityOverlapBasedLabelExtractor;
import de.mpicbg.ulman.fusion.util.SegGtCumulativeScore;
import de.mpicbg.ulman.fusion.util.SegGtImageLoader;
import net.celltrackingchallenge.measures.util.Jaccard;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.Interval;
import net.imglib2.view.Views;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.IntegerType;
import de.mpicbg.ulman.fusion.JobSpecification;

import org.scijava.log.Logger;
import sc.fiji.simplifiedio.SimplifiedIO;

import java.util.Arrays;
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
	void useAlgorithm()
	{
		try { useAlgorithm(null); }
		catch (InterruptedException e) { /* cannot happen 'cause no MT */ }
	}

	public
	void useAlgorithm(final int noOfThreads)
	{
		final ExecutorService w = Executors.newFixedThreadPool(noOfThreads);
		try {
			useAlgorithm(w);
		} catch (InterruptedException e) {
			throw new RuntimeException("Error in multithreading",e);
		} finally {
			w.shutdownNow();
		}
	}


	public
	void useAlgorithm(final ExecutorService threadWorkers)
	throws InterruptedException
	{
		if (algorithm == null)
			throw new RuntimeException("Cannot work without an algorithm.");

		log.info("calling weighted voting algorithm with threshold="+threshold);
		algorithm.setWeights(inWeights);
		algorithm.setThreshold(threshold);
		calcBoxes(threadWorkers);
		outFusedImg = algorithm.fuse(inImgs, markerImg);
	}

	public //NB: because of CMV
	void useAlgorithmWithoutUpdatingBoxes()
	{
		if (algorithm == null)
			throw new RuntimeException("Cannot work without an algorithm.");

		log.info("calling weighted voting algorithm with threshold="+threshold);
		algorithm.setWeights(inWeights);
		algorithm.setThreshold(threshold);
		outFusedImg = algorithm.fuse(inImgs, markerImg);
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


	@Deprecated //waits for Fiji w 1.9+ JVM (since = "processJob(JobSpecification) came to replace this one", forRemoval = true)
	public
	void processJob(final String... args)
	{
		if (algorithm == null)
			throw new RuntimeException("Cannot work without an algorithm.");

		super.loadJob(args);
		useAlgorithm();
		//saveJob(args[args.length-1]);
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

		if (workerThreads != null) {
			super.loadJob(job.instantiateForTime(time), workerThreads);
			useAlgorithm(workerThreads);
		} else {
			super.loadJob(job,time);
			useAlgorithm(null);
		}
	}

	private Img<LT> outFusedImg;

	public Img<LT> getOutFusedImg()
	{ return outFusedImg; }

	public
	void saveJob(final JobSpecification job, final int time)
	{
		final String outFile = JobSpecification.expandFilenamePattern(job.outputPattern,time);
		saveJob( outFile );
	}

	public
	void saveJob(final String outFile)
	{
		log.info("Saving file: "+outFile);
		SimplifiedIO.saveImage(outFusedImg, outFile);
	}

	public
	void scoreJob(final SegGtImageLoader<LT> SEGloader, final SegGtCumulativeScore score)
	{
		log.info("Doing SEG score now...");
		score.startSection();

		//shortcuts:
		final RandomAccessibleInterval<LT> gtImg = SEGloader.lastLoadedImage;
		final Map<Double,long[]> gtBoxes = SEGloader.getLastCalculatedBoxes();
		//
		final RandomAccessibleInterval<LT> resImg = SEGloader.lastLoadedIs2D ?
				Views.hyperSlice(outFusedImg, 2, SEGloader.lastLoaded2DSlice) : outFusedImg;

		//check res and gt images are of the same size/dimensionality
		if (!Arrays.equals(gtImg.minAsLongArray(), resImg.minAsLongArray())
			|| !Arrays.equals(gtImg.maxAsLongArray(), resImg.maxAsLongArray()))
		{
			log.warn("...skipping because of image sizes mismatch.");
			return;
		}

		final Map<Double,long[]> resBoxes = AbstractWeightedVotingRoisFusionAlgorithm.findBoxes(
				resImg,log,"fusion result");

		for (Map.Entry<Double,long[]> gtBox : gtBoxes.entrySet())
		{
			final double gtLabel = gtBox.getKey();
			final Interval gtInterval
					= AbstractWeightedVotingRoisFusionAlgorithm.createInterval(gtBox.getValue());

			final double resLabel = extractor.findMatchingLabel(
					Views.interval(resImg, gtInterval),
					Views.interval(gtImg,  gtInterval),
					(int)gtLabel);
			log.trace("finished the searching, for GT "+gtLabel+" found fusion "+resLabel);

			if (resLabel > 0)
			{
				final long[] resBox = resBoxes.get(resLabel);
				AbstractWeightedVotingRoisFusionAlgorithm.unionBoxes(gtBox.getValue(),resBox);

				final Interval i = AbstractWeightedVotingRoisFusionAlgorithm.createInterval(resBox);
				double seg = Jaccard.Jaccard(Views.interval(resImg,i), resLabel,
						Views.interval(gtImg,i), gtLabel);
				score.addCase(seg);
				log.trace("...with seg = "+seg);
			}
			else score.addCase(0.0); //nothing found for this SEG instance
		}

		log.info("...for this time point "+SEGloader.lastLoadedTimepoint
				+" only: avg SEG = "+score.getSectionScore()+" obtained over "
				+score.getNumberOfSectionCases()+" segments");
	}

	final MajorityOverlapBasedLabelExtractor<LT,LT,?> extractor
			= new MajorityOverlapBasedLabelExtractor<>();

	public
	void releaseJobResult()
	{
		outFusedImg = null;
		log.info("released out img");
	}
}
