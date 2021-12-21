package de.mpicbg.ulman.fusion.ng;

import de.mpicbg.ulman.fusion.ng.extract.MajorityOverlapBasedLabelExtractor;
import de.mpicbg.ulman.fusion.ng.fuse.WeightedVotingLabelFuser;
import de.mpicbg.ulman.fusion.ng.insert.CollisionsManagingLabelInsertor;
import de.mpicbg.ulman.fusion.ng.postprocess.VoidLabelPostprocessor;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.DoubleType;
import org.scijava.log.LogService;

public
class LabelSync2<IT extends RealType<IT>, LT extends IntegerType<LT>>
extends AbstractWeightedVotingFusionAlgorithm<IT,LT>
{
	public LabelSync2(LogService _log) {
		super(_log);
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