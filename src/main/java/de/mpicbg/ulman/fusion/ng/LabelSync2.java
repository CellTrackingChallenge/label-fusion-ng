/*
 * BSD 2-Clause License
 *
 * Copyright (c) 2020,2022, Vladimír Ulman
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

import de.mpicbg.ulman.fusion.ng.extract.MajorityOverlapBasedLabelExtractor;
import de.mpicbg.ulman.fusion.ng.fuse.WeightedVotingLabelFuser;
import de.mpicbg.ulman.fusion.ng.insert.CollisionsManagingLabelInsertor;
import de.mpicbg.ulman.fusion.ng.postprocess.VoidLabelPostprocessor;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.DoubleType;
import org.scijava.log.Logger;

public
class LabelSync2<IT extends RealType<IT>, LT extends IntegerType<LT>>
extends AbstractWeightedVotingFusionAlgorithm<IT,LT,DoubleType>
{
	public LabelSync2(Logger _log) {
		super(_log, new DoubleType());
	}

	@Override
	protected void setFusionComponents() {
		//setup the individual stages
		final MajorityOverlapBasedLabelExtractor<IT,LT, DoubleType> e = new MajorityOverlapBasedLabelExtractor<>();
		e.minFractionOfMarker = 0.5f;

		final WeightedVotingLabelFuser<IT,DoubleType> f = new WeightedVotingLabelFuser<>();
		f.minAcceptableWeight = 0.5f;

		final CollisionsManagingLabelInsertor<LT, DoubleType> i = new CollisionsManagingLabelInsertor<>();
		this.removeMarkersCollisionThreshold = 0.2f;

		final VoidLabelPostprocessor<LT> p = new VoidLabelPostprocessor<>();

		this.labelExtractor = e;
		this.labelFuser     = f;
		this.labelInsertor  = i;
		this.labelCleaner   = p;
	}
}