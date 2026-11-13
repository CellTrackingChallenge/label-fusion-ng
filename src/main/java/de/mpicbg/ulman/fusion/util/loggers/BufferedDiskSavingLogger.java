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
package de.mpicbg.ulman.fusion.util.loggers;

import java.util.List;
import java.util.LinkedList;

public class BufferedDiskSavingLogger extends SimpleDiskSavingLogger implements AutoCloseable
{
	public BufferedDiskSavingLogger() {
		super(".");
	}

	public BufferedDiskSavingLogger(final String logFolder) {
		super(logFolder);
	}

	public BufferedDiskSavingLogger(final String logFolder, final String fileName) {
		super(logFolder, fileName);
	}

	public BufferedDiskSavingLogger(final String logFolder, final String fileName, final String prefix) {
		super(logFolder, fileName, prefix);
	}

	@Override
	public FilebasedLogger create(final String logFolder, final String fileName, final String prefix) {
		BufferedDiskSavingLogger l = new BufferedDiskSavingLogger(logFolder, fileName, prefix);
		additionalCloseables.add(l);
		return l;
	}

	private final List<BufferedDiskSavingLogger> additionalCloseables = new LinkedList<>();


	@Override
	protected void submitThisMsg(final Object origInputMessage, final String finalizedMessage) {
		buffer.append(finalizedMessage);
		if (buffer.length() > bufferFlushThres) flush();
		else buffer.append('\n');
		if (shouldAlsoLeakThis(origInputMessage)) leakingTarget.debug(finalizedMessage);
	}

	private final StringBuffer buffer = new StringBuffer(200 * 1024); //200 kB
	private final int bufferFlushThres = (int)(0.95 * buffer.capacity());

	public void flush() {
		javaLogger.info(buffer.toString());
		buffer.setLength(0);
	}

	@Override
	public void close() {
		flush();
		additionalCloseables.forEach(bufLogger -> {
			try {
				bufLogger.close();
			}
			catch (Exception e) {
				System.out.println("Failing to close the buffered logger '"+bufLogger.prefix+"': "+e.getMessage());
			}
		});
	}
}
