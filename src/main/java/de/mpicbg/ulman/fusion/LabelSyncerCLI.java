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
package de.mpicbg.ulman.fusion;

import de.mpicbg.ulman.fusion.ng.LabelSync2;
import ij.ImagePlus;
import ij.io.Opener;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.ImagePlusAdapter;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import sc.fiji.simplifiedio.SimplifiedIO;

import java.io.File;
import java.util.Set;
import java.util.Vector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class LabelSyncerCLI
{
	// ================= handling filenames =================
	static public
	Set<String> listAllResultMaskFilesInFolder(String dir)
	{
		final File fDir = new File(dir);
		if (!fDir.exists()) return null;

		final File[] files = fDir.listFiles();
		if (files == null || files.length == 0) return null;

		return Stream.of(files)
				.filter(file -> !file.isDirectory())
				//.filter(file -> file.getName().startsWith("res"))
				.filter(file -> file.getName().endsWith("tif"))
				.map(File::getName)
				.collect(Collectors.toSet());
	}

	static public
	String matchTraMarkerFile(String resName, String traDir)
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

	// ================= handling image files =================
	static public
	Img<?> readImageSilently(final String path)
	{
		try {
			final ImagePlus image = new Opener().openImage(path);
			if (image == null) return null;
			return ImagePlusAdapter.wrapImgPlus(image).getImg();
		} catch (Exception e) {
			System.out.println("IO error: "+e.getMessage());
		}
		return null;
	}

	static public <T extends IntegerType<T>>
	boolean isImgFailedAndComplained(final Img<?> img, final T referenceVoxelType)
	{
		if (img == null)
		{
			System.out.println("skipping this one -- failure while reading it");
			return true;
		}
		if (! (img.firstElement().getClass().equals(referenceVoxelType.getClass())) )
		{
			System.out.println("skipping this one -- not the expected pixel type");
			return true;
		}
		return false;
	}

	// ================= the main workhorse code =================
	static public <T extends IntegerType<T>>
	void processImages(String[] args, final T referenceVoxelType)
	{
		//check parameters first
		if (args.length != 3)
		{
			System.out.println("I expect three params: pathToFolderWithResultsFromOneUser pathToFolderWithTRAmarkers outputFolderPath");
			return;
		}

		//prepare the syncing code
		final plugin_GTviaMarkersNG.MyLog myLog = new plugin_GTviaMarkersNG.MyLog();
		final LabelSync2<T,T> labelSync = new LabelSync2<>(myLog);

		//prepare the syncing containers
		Vector<Double> weights = new Vector<>(1);
		weights.add(1.0);
		labelSync.setWeights(weights);

		Vector<RandomAccessibleInterval<T>> imgs = new Vector<>(1);
		imgs.add(null);

		try {
			final Set<String> inFiles = listAllResultMaskFilesInFolder(args[0]);
			if (inFiles == null) {
				System.out.println("Non-existent input folder: "+args[0]);
				return;
			}

			for (String inFile : inFiles) {
				System.out.println("==========================");

				String aFilePath = args[0] + File.separator + inFile;
				System.out.println("Reading input: " + aFilePath);
				//
				Img<?> i = readImageSilently(aFilePath);
				if (isImgFailedAndComplained(i,referenceVoxelType)) continue;
				imgs.setElementAt((Img<T>) i, 0);

				aFilePath = matchTraMarkerFile(inFile, args[1]);
				System.out.println("Reading marker: " + aFilePath);
				//
				Img<?> tra = readImageSilently(aFilePath);
				if (isImgFailedAndComplained(tra,referenceVoxelType)) continue;

				System.out.println("Syncing labels....");
				final Img<T> res = labelSync.fuse(imgs, (Img<T>) tra);

				aFilePath = args[2] + File.separator + inFile;
				System.out.println("Creating output: " + aFilePath);
				SimplifiedIO.saveImage(res, aFilePath);
			}
		} catch (Exception e) {
			System.out.println("error: "+e.getMessage());
			e.printStackTrace();
		}
	}


	public static void main(String[] args)
	{
		processImages(args, new UnsignedShortType());
	}
}