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
package de.mpicbg.ulman.fusion;

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.*;

public class testMultithreading {
	public int counter = 0;

	//--------------------------------------------------
	//common shared reference voxel type
	public Integer fakeVoxelType = -1;

	class LoadOneFile implements Callable<LoadOneFile>
	{
		LoadOneFile(final String filePath) {
			in_filePath = filePath;
		}

		final String in_filePath;
		String out_errorMsg;
		int    out_loadedFileData = 0;

		@Override
		public LoadOneFile call() throws Exception
		{
			System.out.println(in_filePath+": Loading the file...");
			Thread.sleep(4000);
			synchronized (fakeVoxelType) {
				if (fakeVoxelType == -1) {
					fakeVoxelType = 2;
					System.out.println(in_filePath+": First to set the reference voxel type to: "+fakeVoxelType);
				} else {
					System.out.println(in_filePath+": Checking against voxel type: "+fakeVoxelType);
					//todo: check voxel types (and dimensions)
				}
			}

			++counter;
			if (counter != 3) out_loadedFileData = counter; //OK state
			else out_errorMsg = "error loading 3";          //error state

			return this;
		}
	}
	//--------------------------------------------------

	public static void main(String[] args) {
		final testMultithreading tMT = new testMultithreading();
		try {
			List< LoadOneFile > tasks = new ArrayList<>(8);
			for (int i = 0; i < 8; ++i)
				tasks.add( tMT.new LoadOneFile("image"+i+".tif") );

			final ExecutorService workers = Executors.newFixedThreadPool(3);
			List< Future<LoadOneFile> > task_results = workers.invokeAll(tasks);
			System.out.println("INVOKED DONE");

			for (Future<LoadOneFile> f : task_results) {
				LoadOneFile l = f.get();
				if (l.out_errorMsg == null) {
					System.out.println("result of "+l.in_filePath+": loaded as "+l.out_loadedFileData);
				} else {
					System.out.println("result of "+l.in_filePath+": error: "+l.out_errorMsg);
				}
			}
			System.out.println("all loaded.");
			//todo remove executor service
		} catch (InterruptedException e) {
			System.err.println("INTERRUPTION");
			e.printStackTrace();
		} catch (ExecutionException e) {
			System.err.println("EXECUTION");
			e.printStackTrace();
		}
	}
}
