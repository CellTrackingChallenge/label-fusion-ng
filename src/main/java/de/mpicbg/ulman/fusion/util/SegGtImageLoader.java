package de.mpicbg.ulman.fusion.util;

import net.imglib2.img.Img;
import net.imglib2.type.numeric.IntegerType;
import sc.fiji.simplifiedio.SimplifiedIO;

import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.PathMatcher;
import java.util.Optional;

import org.scijava.log.Logger;

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

			//2D format:
			final PathMatcher p2m = segFolder.getFileSystem()
					.getPathMatcher("regex:.*seg_0*" + timepoint + "_[0-9]+\\..*");

			Optional<Path> imageFile = Files.list(segFolder)
					.filter(Files::isRegularFile)
					.filter(p -> p2m.matches(p.getFileName()))
					.findFirst();
			if (imageFile.isPresent())
			{
				lastLoadedImageName = imageFile.get().toFile().getAbsolutePath();
				loadedImage = load2DImage(lastLoadedImageName, timepoint);
			}
			else
			{
				//3D format:
				final PathMatcher p3m = segFolder.getFileSystem()
						.getPathMatcher("regex:.*seg0*" + timepoint + "\\..*");

				imageFile = Files.list(segFolder)
						.filter(Files::isRegularFile)
						.filter(p -> p3m.matches(p.getFileName()))
						.findFirst();
				if (imageFile.isPresent())
				{
					lastLoadedImageName = imageFile.get().toFile().getAbsolutePath();
					loadedImage = load3DImage(lastLoadedImageName, timepoint);
				}
			}

			if (loadedImage != null)
			{
				//check voxel type, cast and store "typed"
				if ( !(loadedImage.firstElement() instanceof IntegerType<?>) )
					throw new IOException("Loaded "+lastLoadedImageName+" of unexpected, non-integer voxel type "
							+lastLoadedImage.firstElement().getClass().getName());
				lastLoadedImage = (Img<LT>)loadedImage;
				log.info("Loaded SEG GT for TP "+lastLoadedTimepoint
						+": " + lastLoadedImageName);
				return true;
			}
		}
		catch (IOException e) {
			throw new RuntimeException("Error loading SEG reference image for timepoint "+timepoint,e);
		}

		log.info("No SEG GT loaded for TP "+lastLoadedTimepoint);
		return false;
	}

	Img<?> load2DImage(final String filePath, final int timepoint)
	{
		lastLoaded2DSlice = extractLastNumber(filePath);
		lastLoadedIs2D = true;
		lastLoadedTimepoint = timepoint;
		lastLoadedImageName = filePath;
		return SimplifiedIO.openImage(filePath).getImg();
	}

	Img<?> load3DImage(final String filePath, final int timepoint)
	{
		lastLoadedIs2D = false;
		lastLoadedTimepoint = timepoint;
		lastLoadedImageName = filePath;
		return SimplifiedIO.openImage(filePath).getImg();
	}

	public int     lastLoadedTimepoint = -1;
	public boolean lastLoadedIs2D = false;
	public int     lastLoaded2DSlice = -1;

	public String  lastLoadedImageName;
	public Img<LT> lastLoadedImage = null;

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
