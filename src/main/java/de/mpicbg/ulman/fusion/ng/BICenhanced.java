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
package de.mpicbg.ulman.fusion.ng;

import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.real.DoubleType;
import org.scijava.log.LogService;

import de.mpicbg.ulman.fusion.ng.extract.MajorityOverlapBasedLabelExtractor;
import de.mpicbg.ulman.fusion.ng.fuse.WeightedVotingLabelFuser;
import de.mpicbg.ulman.fusion.ng.fuse.WeightedVotingLabelFuserWithFailSafe;
import de.mpicbg.ulman.fusion.ng.fuse.ForcedFlatVotingLabelFuserWithFailSafe;
import de.mpicbg.ulman.fusion.ng.postprocess.KeepLargestCCALabelPostprocessor;
import de.mpicbg.ulman.fusion.ng.insert.CollisionsManagingLabelInsertor;

public
class BICenhanced<IT extends RealType<IT>, LT extends IntegerType<LT>>
extends AbstractWeightedVotingFusionAlgorithm<IT,LT>
{
	public
	BICenhanced(final LogService _log)
	{
		super(_log);
	}

	public
	BICenhanced(final LogService _log, final String dbgImgSuffix)
	{
		super(_log);

		//enable debug output
		this.dbgImgFileName = dbgImgSuffix;
	}

	private
	boolean enforceFlatWeightsVoting = false;

	public
	boolean isEnforceFlatWeightsVoting()
	{
		return enforceFlatWeightsVoting;
	}

	public
	void setEnforceFlatWeightsVoting(boolean newState)
	{
		enforceFlatWeightsVoting = newState;
		log.info("BICv2: Override with FLAT weights during voting: "+enforceFlatWeightsVoting);

		final WeightedVotingLabelFuser<IT,DoubleType> f = enforceFlatWeightsVoting ?
			new ForcedFlatVotingLabelFuserWithFailSafe<>() : new WeightedVotingLabelFuserWithFailSafe<>();
		f.minAcceptableWeight = this.threshold;
		this.labelFuser = f;
	}

	@Override
	protected
	void setFusionComponents()
	{
		//setup the individual stages
		final MajorityOverlapBasedLabelExtractor<IT,LT,DoubleType> e = new MajorityOverlapBasedLabelExtractor<>();
		e.minFractionOfMarker = 0.5f;

		final WeightedVotingLabelFuser<IT,DoubleType> f = enforceFlatWeightsVoting ?
			new ForcedFlatVotingLabelFuserWithFailSafe<>() : new WeightedVotingLabelFuserWithFailSafe<>();
		f.minAcceptableWeight = this.threshold;

		final CollisionsManagingLabelInsertor<LT, DoubleType> i = new CollisionsManagingLabelInsertor<>();

		final KeepLargestCCALabelPostprocessor<LT> p = new KeepLargestCCALabelPostprocessor<>();

		this.labelExtractor = e;
		this.labelFuser     = f;
		this.labelInsertor  = i;
		this.labelCleaner   = p;
	}

	/**
	 * This method was added here to make sure that any change in the
	 * voting threshold will be propagated inside this.labelFuser.
	 */
	@Override
	public
	void setThreshold(final double minSumOfWeights)
	{
		super.setThreshold(minSumOfWeights);
		((WeightedVotingLabelFuser)labelFuser).minAcceptableWeight = this.threshold;
	}
}
