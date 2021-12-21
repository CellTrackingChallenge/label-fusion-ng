package de.mpicbg.ulman.fusion;

import de.mpicbg.ulman.fusion.ng.LabelSync2;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import org.scijava.Context;
import org.scijava.log.LogService;
import sc.fiji.simplifiedio.SimplifiedIO;
import sc.fiji.simplifiedio.SimplifiedIOException;

import java.io.File;
import java.util.Set;
import java.util.Vector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class LabelSyncerCLI
{
	static
	public Set<String> listAllResultMaskFilesInFolder(String dir) {
		return Stream.of(new File(dir).listFiles())
				.filter(file -> !file.isDirectory())
				//.filter(file -> file.getName().startsWith("res"))
				.filter(file -> file.getName().endsWith("tif"))
				.map(File::getName)
				.collect(Collectors.toSet());
	}

	static
	public String matchTraMarkerFile(String resName, String traDir)
	{
		//extract number from 'resName'
		int digitsFrom = 0;
		while (digitsFrom < resName.length() && (resName.charAt(digitsFrom) < 48 || resName.charAt(digitsFrom) > 58))
			digitsFrom++;

		//did it found digits?
		if (digitsFrom == resName.length()) return null;

		int digitsTill = digitsFrom;
		while (digitsTill < resName.length() && resName.charAt(digitsTill) >= 48 && resName.charAt(digitsTill) <= 58)
			digitsTill++;

		return traDir+File.separator+"man_track"+resName.substring(digitsFrom,digitsTill)+".tif";
	}

	public static void main(String[] args) {
		//check parameters first
		if (args.length != 3)
		{
			System.out.println("I expect three params: pathToFolderWithResultsFromOneUser pathToFolderWithTRAmarkers outputFolderPath");
			return;
		}

		//prepare the syncing code
		final LogService myLog = new Context(LogService.class).getService(LogService.class);
		final LabelSync2<UnsignedShortType,UnsignedShortType> labelSync = new LabelSync2<>(myLog);

		//prepare the syncing containers
		Vector<Double> weights = new Vector<>(1);
		weights.add(1.0);
		labelSync.setWeights(weights);

		Vector<RandomAccessibleInterval<UnsignedShortType>> imgs = new Vector<>(1);
		imgs.add(null);

		try {
			for (String inFile : listAllResultMaskFilesInFolder(args[0])) {
				String aFilePath = args[0] + File.separator + inFile;
				System.out.println("Reading input: " + aFilePath);
				//
				Img<?> i = readImageSilently(aFilePath);
				if (isImgFailedAndComplained(i)) continue;
				imgs.setElementAt((Img<UnsignedShortType>) i, 0);

				aFilePath = matchTraMarkerFile(inFile, args[1]);
				System.out.println("Reading marker: " + aFilePath);
				//
				Img<?> tra = readImageSilently(aFilePath);
				if (isImgFailedAndComplained(tra)) continue;

				System.out.println("Syncing labels....");
				final Img<UnsignedShortType> res = labelSync.fuse(imgs, (Img<UnsignedShortType>) tra);

				aFilePath = args[2] + File.separator + inFile;
				System.out.println("Creating output: " + aFilePath);
				SimplifiedIO.saveImage(res, aFilePath);

				System.out.println("==========================");
			}
		} catch (SimplifiedIOException e) {
			System.out.println("SIO ex: "+e.getMessage());
		}
	}

	static public
	Img<?> readImageSilently(final String path)
	{
		try {
			return SimplifiedIO.openImage(path).getImg();
		} catch (SimplifiedIOException e) {
			System.out.println("IO error: "+e.getMessage());
		}
		return null;
	}

	static public
	boolean isImgFailedAndComplained(final Img<?> img)
	{
		if (img == null)
		{
			System.out.println("skipping this one -- failure while reading it");
			return true;
		}
		if (! (img.firstElement() instanceof UnsignedShortType))
		{
			System.out.println("skipping this one -- not gray16 per pixel");
			return true;
		}
		return false;
	}
}