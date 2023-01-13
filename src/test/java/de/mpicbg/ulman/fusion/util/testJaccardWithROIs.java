package de.mpicbg.ulman.fusion.util;

import net.imglib2.Interval;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.cell.CellImgFactory;
import net.imglib2.roi.labeling.BoundingBox;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.view.Views;
import sc.fiji.simplifiedio.SimplifiedIO;

public class testJaccardWithROIs {
	static
	<T extends IntegerType<T> & NativeType<T>>
	Img<T> createArrayImg(final T refType, long... size) {
		return new ArrayImgFactory<>(refType).create(size);
	}

	static
	<T extends IntegerType<T> & NativeType<T>>
	Img<T> createCellImg(final T refType, long... size) {
		return new CellImgFactory<>(refType).create(size);
	}

	static
	<T extends IntegerType<T>>
	void fillRoi(final Img<T> img, final Interval roi, final T value) {
		Views.interval(img,roi).forEach(p -> p.set(value));
	}

	static
	<T extends NativeType<T>>
	void saveImg(final Img<T> img, final String filename) {
		SimplifiedIO.saveImage(img, filename);
	}

	public static void main(String[] args) {
		final Img<UnsignedShortType> a = createArrayImg(new UnsignedShortType(), 200,100);
		final Img<UnsignedShortType> b = createCellImg(new UnsignedShortType(), 100,200);

		fillRoi(a,a,new UnsignedShortType((short) 10));
		fillRoi(b,b,new UnsignedShortType((short) 20));
		saveImg(a,"/temp/a0.tif");
		saveImg(b,"/temp/b.tif");
		//
		System.out.println("0.333 = "
				+ JaccardWithROIs.JaccardLB(a,10,a, b,20,b));

		BoundingBox bb = new BoundingBox(2);
		bb.update(new long[] {100,0});
		bb.update(new long[] {199,99});
		fillRoi(a, bb, new UnsignedShortType(0));
		saveImg(a,"/temp/a1.tif");
		//
		System.out.println("0.5 = "
				+ JaccardWithROIs.JaccardLB(a,10,a, b,20,b));

		bb.update(new long[] {50,0});
		fillRoi(a, bb, new UnsignedShortType(0));
		saveImg(a,"/temp/a2.tif");
		//
		System.out.println("0.25 = "
				+ JaccardWithROIs.JaccardLB(a,10,a, b,20,b));

		bb = new BoundingBox(2);
		bb.update(new long[] {50,0});
		bb.update(new long[] {99,99});
		System.out.println("0.0 = "
				+ JaccardWithROIs.JaccardLB(a,10,a, b,20,bb));
		//beware of the design!
		//here, intersection of 'bb' and 'a' is nothing but 'b' finds 'a' in its roi....
		System.out.println("0.333 = "
				+ JaccardWithROIs.JaccardLB(a,10,bb, b,20,b));

		bb.update(new long[] {0,0});
		System.out.println("bbox: "+bb);
		System.out.println("0.25 = "
				+ JaccardWithROIs.JaccardLB(a,10,bb, b,20,b));
		System.out.println("0.5 = "
				+ JaccardWithROIs.JaccardLB(a,10,bb, b,20,bb));
	}
}
