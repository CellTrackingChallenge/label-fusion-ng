package de.mpicbg.ulman.fusion.util;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.view.Views;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;
import sc.fiji.simplifiedio.SimplifiedIO;
import sc.fiji.simplifiedio.SimplifiedIOException;
import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.PathMatcher;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Collections;
import java.util.stream.Collectors;
import org.scijava.log.Logger;
import de.mpicbg.ulman.fusion.ng.AbstractWeightedVotingRoisFusionAlgorithm;

public class SegGtImageLoader<LT extends IntegerType<LT>>
{
	final Logger log;
	final Path segFolder;

	public SegGtImageLoader(final String segFolderPath, final Logger _log)
	{
		log = _log;
		segFolder = Paths.get(segFolderPath);

		//sanity checks...
		if (!Files.isDirectory(segFolder))
			throw new InvalidPathException(segFolderPath,"Path is not a directory");
		if (!Files.isReadable(segFolder))
			throw new InvalidPathException(segFolderPath,"Path is not readable");
	}

	public boolean managedToLoadImageForTimepoint(final int timepoint)
	{
		try {
			Img<?> loadedImage = null;
			lastLoadedData.clear();

			//2D format:
			final PathMatcher p2m = segFolder.getFileSystem()
					.getPathMatcher("regex:.*seg_0*" + timepoint + "_[0-9]+\\..*");

			List<Path> discoveredImgFiles = Files.list(segFolder)
					.filter(Files::isRegularFile)
					.filter(p -> p2m.matches(p.getFileName()))
					.collect(Collectors.toList());

			if (discoveredImgFiles.size() > 0)
			{
				//2D format:
				while (discoveredImgFiles.size() > 0)
				{
					final LoadedData ld = new LoadedData();
					ld.lastLoadedImageName = discoveredImgFiles.remove(0).toFile().getAbsolutePath();
					ld.lastLoadedTimepoint = timepoint;
					loadedImage = load2DImage(ld);
					checkCastAndStoreOrThrow(loadedImage, ld);
					lastLoadedData.add(ld);
				}
			}
			else
			{
				//3D format:
				final PathMatcher p3m = segFolder.getFileSystem()
						.getPathMatcher("regex:.*seg0*" + timepoint + "\\..*");

				discoveredImgFiles = Files.list(segFolder)
						.filter(Files::isRegularFile)
						.filter(p -> p3m.matches(p.getFileName()))
						.limit(1)
						.collect(Collectors.toList());

				while (discoveredImgFiles.size() > 0)
				{
					final LoadedData ld = new LoadedData();
					ld.lastLoadedImageName = discoveredImgFiles.remove(0).toFile().getAbsolutePath();
					ld.lastLoadedTimepoint = timepoint;
					loadedImage = load3DImage(ld);
					checkCastAndStoreOrThrow(loadedImage, ld);
					lastLoadedData.add(ld);
				}
			}
		}
		catch (IOException | SimplifiedIOException e) {
			throw new RuntimeException("Error loading SEG reference image for timepoint "
					+timepoint+": "+e.getMessage(),e);
		}

		if (!lastLoadedData.isEmpty()) {
			return true;
		} else {
			log.info("No SEG GT loaded for TP "+timepoint);
			return false;
		}
	}

	void checkCastAndStoreOrThrow(final Img<?> loadedImage, final LoadedData ld)
	throws IOException
	{
		if (loadedImage == null)
			throw new IOException("Failed loading "+ld.lastLoadedImageName);

		//check voxel type
		if ( !(loadedImage.firstElement() instanceof IntegerType<?>) )
			throw new IOException("Loaded "+ld.lastLoadedImageName+" of unexpected, non-integer voxel type "
					+loadedImage.firstElement().getClass().getName());

		//cast and store "typed"
		ld.lastLoadedImage = (Img<LT>)loadedImage;
		log.info("Loaded SEG GT for TP "+ld.lastLoadedTimepoint
				+": " + ld.lastLoadedImageName);
	}

	Img<?> load2DImage(final LoadedData ld)
	{
		final String filePath = ld.lastLoadedImageName;
		ld.lastLoaded2DSlice = extractLastNumber(filePath);
		ld.lastLoadedIs2D = true;
		return SimplifiedIO.openImage(filePath).getImg();
	}

	Img<?> load3DImage(final LoadedData ld)
	{
		final String filePath = ld.lastLoadedImageName;
		ld.lastLoadedIs2D = false;
		return SimplifiedIO.openImage(filePath).getImg();
	}

	public class LoadedData
	{
		public int     lastLoadedTimepoint = -1;
		public boolean lastLoadedIs2D = false;
		public int     lastLoaded2DSlice = -1;

		public String  lastLoadedImageName;
		public Img<LT> lastLoadedImage = null;

		public Map<Double,long[]> calculatedBoxes = null;
		//
		public void calcBoxes()
		{
			calculatedBoxes = AbstractWeightedVotingRoisFusionAlgorithm.findBoxes(
					lastLoadedImage,log,"SEG GT");
		}

		public <A extends RealType<A>>
		RandomAccessibleInterval<A> slicedViewOf(final RandomAccessibleInterval<A> img)
		{
			return lastLoadedIs2D ? Views.hyperSlice(img, 2, lastLoaded2DSlice) : img;
		}
	}

	public List<LoadedData> getLastLoadedData()
	{
		return Collections.unmodifiableList(lastLoadedData);
	}

	private final List<LoadedData> lastLoadedData = new ArrayList<>(5);


	public static int extractLastNumber(final String tiffFilename)
	{
		int dotPos = tiffFilename.lastIndexOf('.');
		if (dotPos == -1)
			throw new InvalidPathException(tiffFilename,"Path does not include .tif suffix");

		int startPos = dotPos-1;
		while (startPos >= 0
				&& tiffFilename.charAt(startPos) >= '0'
				&& tiffFilename.charAt(startPos) <= '9') --startPos;

		if (startPos == dotPos-1)
			throw new InvalidPathException(tiffFilename,"Path does not include any number");

		return Integer.parseInt(tiffFilename.substring(startPos+1,dotPos), 10);
	}
}
