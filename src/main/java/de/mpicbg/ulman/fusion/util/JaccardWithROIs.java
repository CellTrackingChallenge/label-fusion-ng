package de.mpicbg.ulman.fusion.util;

import net.imglib2.Interval;
import net.imglib2.IterableInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.loops.LoopBuilder;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.Views;

import java.util.concurrent.atomic.AtomicLong;

public class JaccardWithROIs {
	/**
	 * Calculates Jaccard (intersection over union) for two masks, each given
	 * with its label and considered only within its roi (region of interest).
	 *
	 * @param imgA one image with the mask (examined image)
	 * @param labelA label of pixels that make up this mask
	 * @param roiA no pixels outside this region will be considered (and will not be visited)
	 * @param imgB another image with its mask (reference image)
	 * @param labelB label of pixels that make up this mask
	 * @param roiB no pixels outside this region will be considered (and will not be visited)
	 * @return Jaccard value
	 */
	static public <TA extends RealType<TA>, TB extends RealType<TB>>
	double JaccardLB(final RandomAccessibleInterval<TA> imgA, final double labelA, final Interval roiA,
	                 final RandomAccessibleInterval<TB> imgB, final double labelB, final Interval roiB)
	{
		//plan:
		//count size within imgA
		//count pixels in intersection of A and B
		//count pixels in B and not in A

		final AtomicLong sizeA = new AtomicLong();
		Views.interval(imgA,roiA).forEach(px -> sizeA.addAndGet(px.getRealDouble() == labelA ? 1 : 0));

		final AtomicLong sizeBalone = new AtomicLong();
		final AtomicLong sizeBandA = new AtomicLong();

		LoopBuilder.setImages(
				Views.interval( Views.extendValue(imgA, 0), roiB ),
				Views.interval( imgB, roiB )
		).flatIterationOrder().forEachPixel( (a,b) -> {
			if (b.getRealDouble() == labelB) {
				if (a.getRealDouble() == labelA)
					sizeBandA.incrementAndGet();
				else
					sizeBalone.incrementAndGet();
			}
		});

		return (double)sizeBandA.get() / (double)(sizeA.get() + sizeBalone.get());
	}
}