package de.mpicbg.ulman.fusion.util;

import de.mpicbg.ulman.fusion.ng.AbstractWeightedVotingRoisFusionAlgorithm;
import de.mpicbg.ulman.fusion.ng.insert.CollisionsManagingLabelInsertor.PxCoord;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedIntType;

import org.scijava.log.Logger;
import de.mpicbg.ulman.fusion.util.loggers.NoOutputLogger;

import java.util.List;
import java.util.ArrayList;
import java.util.Vector;
import java.util.Map;
import java.util.HashMap;

/** A singleton memory broker to avoid re-allocating one-time used memory */
public class ReusableMemory<LT extends IntegerType<LT>, ET extends RealType<ET>>
{
	private static final int EXPECTED_BORROWERS_NUM = 20;

	private final LT refLabelType;
	private final ET refExtType;
	private final UnsignedIntType refIntType = new UnsignedIntType();
	private final Img<?> refImage;

	private Img<LT> createLabelImage() {
		return refImage.factory().imgFactory(refLabelType).create(refImage);
	}

	private Img<ET> createExtImage() {
		return refImage.factory().imgFactory(refExtType).create(refImage);
	}

	private Vector<PxCoord> createPx() {
		return new Vector<>(500000);
	}

	private Map<Long,Integer> createCatalogue() {
		return new HashMap<>(5000);
	}


	private <LLT extends IntegerType<LLT>>
	void checkForMatchOrThrow(final Img<?> refImage,
	                          final LLT refLabelType)
	throws RuntimeException
	{
		if (!refLabelType.getClass().equals(this.refLabelType.getClass()))
			throw new RuntimeException("Labels type mismatch");

		final int refDim = this.refImage.numDimensions();
		final int givenDim = refImage.numDimensions();
		if (refDim != givenDim)
			throw new RuntimeException("Reference images mismatch in dimensionality");

		for (int n = 0; n < refDim; ++n)
			if (this.refImage.dimension(n) != refImage.dimension(n))
				throw new RuntimeException("Reference images mismatch in size for dim "+n);
	}

	private <LLT extends IntegerType<LLT>, EET extends RealType<EET>>
	void checkForMatchOrThrow(final Img<?> refImage,
	                          final LLT refLabelType,
	                          final EET refExtType)
	throws RuntimeException
	{
		checkForMatchOrThrow(refImage,refLabelType);

		if (!refExtType.getClass().equals(this.refExtType.getClass()))
			throw new RuntimeException("Aux type mismatch");
	}


	//the shared data -- same-dimensional images
	private final List<Img<ET>> tmpImgs = new ArrayList<>(EXPECTED_BORROWERS_NUM);
	private final List<Img<LT>> outImgs = new ArrayList<>(EXPECTED_BORROWERS_NUM);
	private final List<Img<LT>> ccaInImgs = new ArrayList<>(EXPECTED_BORROWERS_NUM);
	private final List<Img<LT>> ccaOutImgs = new ArrayList<>(EXPECTED_BORROWERS_NUM);

	private final List<Vector<PxCoord>> interesectionPxs = new ArrayList<>(EXPECTED_BORROWERS_NUM);
	private final List<Vector<PxCoord>> tempHiddenPxs = new ArrayList<>(EXPECTED_BORROWERS_NUM);
	private final List<Map<Long,Integer>> intersectionCatalogues = new ArrayList<>(EXPECTED_BORROWERS_NUM);

	/**
	 * Borrows "tmpImg" to this caller, and blocks the other images from this object (the singleton)
	 * for the same caller. The borrowed image(s) are of the same dimension, sizes and voxel
	 * types for which this singleton was created with {@link #resetTo(Img, IntegerType, RealType)}
	 * or {@link #getInstanceFor(Img, IntegerType, RealType)}.
	 *
	 * @param borrowerID An unique non-zero designation of the borrower
	 * @return Image for temporary storage of the "ET" (from the fusion world) voxel type.
	 */
	public Img<ET> getTmpImg(final int borrowerID) {
		return tmpImgs.get( register(borrowerID) );
	}

	/** See {@link #getTmpImg(int)} */
	public Img<LT> getOutImg(final int borrowerID) {
		return outImgs.get( register(borrowerID) );
	}

	/** See {@link #getTmpImg(int)} */
	public Img<LT> getCcaInImg(final int borrowerID) {
		return ccaInImgs.get( register(borrowerID) );
	}

	/** See {@link #getTmpImg(int)} */
	public Img<LT> getCcaOutImg(final int borrowerID) {
		return ccaOutImgs.get( register(borrowerID) );
	}

	public Vector<PxCoord> getInteresectionPx(final int borrowerID) {
		return interesectionPxs.get( register(borrowerID) );
	}
	public Vector<PxCoord> getTempHiddenPx(final int borrowerID) {
		return tempHiddenPxs.get( register(borrowerID) );
	}
	public Map<Long,Integer> getCatalogue(final int borrowerID) {
		return intersectionCatalogues.get( register(borrowerID) );
	}

	/**
	 * Informs this object (the singleton) that the caller will no longer touch
	 * the borrowed images, making them available for another caller.
	 *
	 * @param borrowerID An unique non-zero designation of the borrower
	 */
	public void closeSession(final int borrowerID) {
		unregister(borrowerID);
	}


	//the sharing lists (aka list of currently registered) as mappings in both directions
	private final List<Integer> dataToSubject = new ArrayList<>(EXPECTED_BORROWERS_NUM);
	private final Map<Integer,Integer> subjectToData = new HashMap<>(EXPECTED_BORROWERS_NUM);
	private static final int VACANT_SLOT = 0;

	private int register(final int borrowerID)
	{
		synchronized (SYNCHRONIZER)
		{
			//already registered?
			if (subjectToData.containsKey(borrowerID)) {
				//log.debug("ReusableMem.registering: known borrower "+borrowerID+" will get its slot "+subjectToData.get(borrowerID));
				return subjectToData.get(borrowerID);
			}

			//is there some empty slot?
			for (int i = 0; i < dataToSubject.size(); ++i) {
				if (dataToSubject.get(i) == VACANT_SLOT) {
					dataToSubject.set(i,borrowerID);
					subjectToData.put(borrowerID,i);
					//log.debug("ReusableMem.registering: new borrower "+borrowerID+" will re-use slot "+subjectToData.get(borrowerID));
					return i;
				}
			}

			//create new...
			dataToSubject.add( borrowerID );
			final int new_i = dataToSubject.size()-1;
			subjectToData.put( borrowerID, new_i );
			tmpImgs.add( createExtImage() );
			outImgs.add( createLabelImage() );
			ccaInImgs.add( createLabelImage() );
			ccaOutImgs.add( createLabelImage() );
			interesectionPxs.add( createPx() );
			tempHiddenPxs.add( createPx() );
			intersectionCatalogues.add( createCatalogue() );
			//log.debug("ReusableMem.registering: new borrower "+borrowerID+" will get new slot "+subjectToData.get(borrowerID));
			return new_i;
		}
	}

	private void unregister(final int borrowerID)
	{
		synchronized (SYNCHRONIZER)
		{
			//was registered?
			if (!subjectToData.containsKey(borrowerID)) {
				//log.debug("ReusableMem.DEregistering: unknown borrower "+borrowerID);
				return;
			}

			//log.debug("ReusableMem.DEregistering: known borrower "+borrowerID+" from slot "+subjectToData.get(borrowerID));
			dataToSubject.set( subjectToData.get(borrowerID), VACANT_SLOT );
			subjectToData.remove(borrowerID);
		}
	}

	@Override
	public String toString()
	{
		synchronized (SYNCHRONIZER)
		{
			StringBuilder sb = new StringBuilder("ReMem instance "+getAddr()+"\nSlots:");
			for (int i = 0; i < dataToSubject.size(); ++i)
				sb.append(" [").append(i).append(": ")
						.append(dataToSubject.get(i))
						.append(']');
			sb.append("\nBorrowers:");
			for (int who : subjectToData.keySet())
				sb.append(" [").append(who).append(" owns ")
						.append(subjectToData.get(who))
						.append(']');
			sb.append("\nAllocated sizes per slot:\n");
			//
			final long LTpxSize = refLabelType.getBitsPerPixel()/8;
			final long ETpxSize = refExtType.getBitsPerPixel()/8;
			final long intPxSize = refIntType.getBitsPerPixel()/8;
			for (int i = 0; i < dataToSubject.size(); ++i)
			{
				sb.append("  outImg["+i+"]: "
						+AbstractWeightedVotingRoisFusionAlgorithm.reportImageSize(
								outImgs.get(i), LTpxSize) +"\n");
				sb.append("  tmpImg["+i+"]: "
						+AbstractWeightedVotingRoisFusionAlgorithm.reportImageSize(
								tmpImgs.get(i), ETpxSize) +"\n");
				sb.append("  ccaInImg["+i+"]: "
						+AbstractWeightedVotingRoisFusionAlgorithm.reportImageSize(
								ccaInImgs.get(i), LTpxSize) +"\n");
				sb.append("  ccaOutImg["+i+"]: "
						+AbstractWeightedVotingRoisFusionAlgorithm.reportImageSize(
								ccaOutImgs.get(i), LTpxSize) +"\n");
				sb.append("  intersectionPxs["+i+"] vector of capacity: "+interesectionPxs.get(i).capacity()+"\n");
				sb.append("  temp_hidden_Pxs["+i+"] vector of capacity: "+tempHiddenPxs.get(i).capacity()+"\n");
				sb.append("  intersectionCatalogue["+i+"] map of items: "+intersectionCatalogues.get(i).size()+"\n---\n");
			}
			return sb.toString();
		}
	}

	public String getAddr()
	{
		return super.toString();
	}


	// -------- singleton business --------
	private ReusableMemory(final Img<?> refImage,
	                       final LT refLabelType,
	                       final ET refExtType)
	{
		this.refImage = refImage;
		this.refLabelType = refLabelType;
		this.refExtType = refExtType;
	}

	/**
	 * This one represents the singleton instance, and this instance can be
	 * changed anytime later, see {@link #resetTo(Img, IntegerType, RealType)}}.
	 */
	private static ReusableMemory<?,?> THE_INSTANCE = null;

	/** immutable singleton used solely for the synchronization purposes
	    (which exists here because the working singleton instance can be changed) */
	private static final Object SYNCHRONIZER = new Object();

	/**
	 * Returns a "handle" on the shared singleton {@link #THE_INSTANCE} provided
	 * the singleton was previously designed for exactly the same given image size and voxel types
	 * (aka compatible input). If the singleton exists but is incompatible, a {@link RuntimeException}
	 * is thrown. If no singleton existed before, it is created on this occasion, and returned.
	 *
	 * @param refImage Template image, the singleton will hold several copies of such image to borrow to the clients
	 * @param refLabelType Template voxel type for labels
	 * @param refExtType Template voxel type for the aux (tmp, helper) images
	 * @param <LLT> local "LT" type from the fusion world (Label Type)
	 * @param <EET> local "ET" type from the fusion world (External Type)
	 * @return Reference on the singleton that can be borrowing exactly such data.
	 */
	public static <LLT extends IntegerType<LLT>, EET extends RealType<EET>>
	ReusableMemory<LLT,EET> getInstanceFor(final Img<?> refImage,
	                                       final LLT refLabelType,
	                                       final EET refExtType)
	{
		synchronized (SYNCHRONIZER)
		{
			if (THE_INSTANCE == null)
				resetTo(refImage,refLabelType,refExtType);
			else
				THE_INSTANCE.checkForMatchOrThrow(refImage,refLabelType,refExtType);

			return (ReusableMemory<LLT, EET>)THE_INSTANCE;
		}
	}

	/**
	 * Similar to {@link #getInstanceFor(Img, IntegerType, RealType)} but the underlying
	 * singleton {@link #THE_INSTANCE} must be already existing. This method is provided
	 * for callers that don't care about the "ET" (External Type) and that will thus
	 * not use {@link #getTmpImg(int)}.
	 * TODO: should "layer" this class and return the limited one here
	 *
	 * @param refImage Template image, the singleton will hold several copies of such image to borrow to the clients
	 * @param refLabelType Template voxel type for labels
	 * @param <LLT> local "LT" type from the fusion world (Label Type)
	 * @return Reference on the singleton that can be borrowing exactly such data.
	 */
	public static <LLT extends IntegerType<LLT>>
	ReusableMemory<LLT,?> getInstanceFor(final Img<?> refImage,
	                                     final LLT refLabelType)
	{
		synchronized (SYNCHRONIZER)
		{
			if (THE_INSTANCE == null)
				throw new RuntimeException("Cannot create ReusableMemory singleton here, incomplete information was given");
			else
				THE_INSTANCE.checkForMatchOrThrow(refImage,refLabelType);

			return (ReusableMemory<LLT, ?>)THE_INSTANCE;
		}
	}

	/**
	 * Re-creates the singleton to start borrowing data according to the new template params.
	 * See {@link #getInstanceFor(Img, IntegerType, RealType)} for details.
	 */
	public static <LLT extends IntegerType<LLT>, EET extends RealType<EET>>
	ReusableMemory<LLT,EET> resetTo(final Img<?> refImage,
	                                final LLT refLabelType,
	                                final EET refExtType)
	{
		synchronized (SYNCHRONIZER)
		{
			THE_INSTANCE = new ReusableMemory<>(refImage,refLabelType,refExtType);
			log.debug("resetting into a new ReMem instance of "+THE_INSTANCE.getAddr());
			return (ReusableMemory<LLT, EET>)THE_INSTANCE;
		}
	}


	/**
	 * Returns/obtains/provides ID for a caller, and that can be used as borrowerID,
	 * see {@link #register(int)}.
	 */
	public static int getThreadId()
	{
		return (int)Thread.currentThread().getId();
	}


	// -------- logging business --------
	/** a shared logger that debug-informs about some housekeeping activities */
	private static Logger log = new NoOutputLogger();

	public static Logger getLogger()
	{ return log; }

	public static void setLogger(final Logger newLogger)
	{ log = newLogger; }

	public static void setNoLogging()
	{ log = new NoOutputLogger(); }
}
