/*
 * BSD 2-Clause License
 *
 * Copyright (c) 2023, Vladimír Ulman
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
package de.mpicbg.ulman.fusion.ng;

import de.mpicbg.ulman.fusion.ng.extract.LabelExtractorForCherryPicker;
import de.mpicbg.ulman.fusion.ng.fuse.LabelPicker;
import de.mpicbg.ulman.fusion.ng.insert.LabelPreservingInsertor;
import de.mpicbg.ulman.fusion.ng.postprocess.VoidLabelPostprocessor;
import de.mpicbg.ulman.fusion.util.SegGtImageLoader;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.ByteType;
import org.scijava.log.Logger;

public
class CherryPickerOfSourceIDs<IT extends RealType<IT>, LT extends IntegerType<LT>>
extends CherryPicker<IT,LT>
{
	public CherryPickerOfSourceIDs(final Logger _log, final SegGtImageLoader<LT> _segImgLoader) {
		super(_log, _segImgLoader);
	}

	@Override
	protected void setFusionComponents() {
		//setup the individual stages
		//btw, this one is called _before_ the local part of the c'tor
		extractorForCherryPicker = new LabelExtractorForCherryPicker<>();
		extractorForCherryPicker.minFractionOfMarker = 0.5f;

		final LabelPicker<IT, ByteType> f = new LabelPicker<>();
		final LabelPreservingInsertor<LT, ByteType> i = new LabelPreservingInsertor<>();
		final VoidLabelPostprocessor<LT> p = new VoidLabelPostprocessor<>();

		this.labelExtractor = extractorForCherryPicker;
		this.labelFuser     = f;
		this.labelInsertor  = i;
		this.labelCleaner   = p;
	}
}
