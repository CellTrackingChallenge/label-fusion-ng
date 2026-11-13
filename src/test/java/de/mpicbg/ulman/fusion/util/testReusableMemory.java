/*-
 * #%L
 * label-fusion-ng
 * %%
 * Copyright (C) 2020 - 2026 Vladimír Ulman
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package de.mpicbg.ulman.fusion.util;

import de.mpicbg.ulman.fusion.util.loggers.SimpleConsoleLogger;
import net.imglib2.exception.IncompatibleTypeException;
import net.imglib2.img.Img;
import net.imglib2.img.planar.PlanarImgFactory;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.scijava.log.Logger;
import de.mpicbg.ulman.fusion.util.loggers.TimeStampedConsoleLogger;

public class testReusableMemory {
	final static Img<UnsignedShortType> goodImg = new PlanarImgFactory<>(new UnsignedShortType()).create(1024,1024,100);
	final static Img<UnsignedShortType> badImg = new PlanarImgFactory<>(new UnsignedShortType()).create(1024,24,24);
	final static UnsignedShortType lt = new UnsignedShortType(40);
	final static FloatType et = new FloatType(40.4f);

	final static Logger mainLog = new TimeStampedConsoleLogger();

	static class Worker implements Callable<Long> {
		final int id;
		final Logger log;

		Worker(int id) {
			this.id = id;
			log = mainLog.subLogger("W_"+id+" ");
		}

		@Override
		public Long call() throws Exception {
			long tid = Thread.currentThread().getId();
			try {
				long sleepTime = (id - 98) * 2000;
				log.info("Worker " + id + " was started on thread " + tid
						+ ", sleeping periods of " + sleepTime);

				ReusableMemory<?,?> mem = id == 107
						? ReusableMemory.resetTo(badImg, new UnsignedShortType(), et) //for 107
						: ( id == 109
							? ReusableMemory.getInstanceFor(badImg, lt, et)    //for 109
							: ReusableMemory.getInstanceFor(goodImg, lt, et)   //for the rest
						);
				log.info("Worker " + id + " got singleton: " + mem);

				//Thread.sleep(sleepTime);
				//log.info("Worker " + id + " still with singleton: " + mem);
				Img<?> img = mem.getTmpImg(id);

				log.info("Worker " + id + " has image: " + img);
				log.info(mem.toString());

				Thread.sleep(sleepTime);
				mem.closeSession(id);
			} catch (IncompatibleTypeException e) {
				log.info("TYPES: "+e.getMessage());
			} catch (Exception e) {
				log.info("EEE: "+e.getMessage());
				//e.printStackTrace();
			}
			return tid;
		}
	}

	public static void main(String[] args) {
		final int NO_OF_WORKERS = 7;
		List<Worker> pool = new ArrayList<>(NO_OF_WORKERS);
		for (int i = 0; i < NO_OF_WORKERS; ++i)
			pool.add(new Worker(100+i));

		try {
			ExecutorService service = Executors.newFixedThreadPool(3);
			mainLog.info("Starting...");
			service.invokeAll(pool);
			mainLog.info("Closing...");
			service.shutdownNow();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	public static void main_testAddrReporting(String[] args) {
		ReusableMemory.setLogger( new SimpleConsoleLogger() );
		System.out.println("take 1:");
		ReusableMemory.getInstanceFor(badImg,lt,et);

		System.out.println("take 2:");
		ReusableMemory.getInstanceFor(badImg,lt);

		System.out.println("take 3:");
		ReusableMemory.resetTo(badImg,lt,et);
	}
}
