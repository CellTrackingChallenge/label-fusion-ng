package de.mpicbg.ulman.fusion;

import de.mpicbg.ulman.fusion.ng.LabelSync;
import de.mpicbg.ulman.fusion.ng.LabelSync.nameFormatTags;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import org.scijava.Context;
import org.scijava.log.LogService;
import sc.fiji.simplifiedio.SimplifiedIO;

import java.util.Vector;

import static de.mpicbg.ulman.fusion.testMergingAPI.createFakeSegmentation;
import static de.mpicbg.ulman.fusion.testMergingAPI.createFakeTRA;

public class testLabelSyncing {
    //debug params...
    static final boolean saveInputsForInspection = true;
    static final boolean saveOutputsForInspection = true;

    public static void main(String[] args) {
        final LogService myLog = new Context(LogService.class).getService(LogService.class);
        //myLog.setLevel(LogService.TRACE);

        //create the label sync, for the last param decide
        //if perLabel (true) or perImage (false) regime
        LabelSync<UnsignedByteType,UnsignedShortType> labelSync = new LabelSync<>(myLog);

        //decide on exported images: if perLabel (which is default) or perImage
        labelSync.wantPerLabelProcessing = false;

        //tell timepoint and start it,
        //this needs to be updated every time prior *ToDisk() calls
        labelSync.currentTime = 99;

        //optional param: if not set, all labels are used
        labelSync.syncOnlyLabels(2,5);

        //optional param: if not set, default overlap ratio of 0.5 is used
        labelSync.setMinOverlapOverTRA(0.9f);

        //optional param: if perImage regime, reset the output filename patter
        if (!labelSync.wantPerLabelProcessing)
        {
            //filename string, may (should...) include path,
            //must include %d placeholders, or %0Xd for 0-padded X-wide numbers
            labelSync.outputFilenameFormat = "source%d__time%03d.tif";
            //
            //define the semantics of the %d placeholders, in the left->right order
            labelSync.outputFilenameOrder = new nameFormatTags[] { nameFormatTags.source, nameFormatTags.time};
        }

        // ---------------- fake input data ----------------
        //storage for tra markers for their x,y centre coordinates (so array must be twice the number of markers)
        final int segInputsCnt = 3;
        final int traMarkersCnt = 5;
        final int[] centres = new int[2*traMarkersCnt];

        //the TRA markers image (fills also the 'centres')
        final Img<UnsignedShortType> traImg = createFakeTRA(centres);

        //the collections of input instance segmentations and their weights
        Vector<RandomAccessibleInterval<UnsignedByteType>> segImgs = new Vector<>(segInputsCnt);
        for (int i = 0; i < segInputsCnt; ++i)
        {
            //creates a fake cell segments around the tra centres with some "random" shift
            //(so that not all seg inputs are the same)
            segImgs.add( createFakeSegmentation( new int[] {(i*3)%5, (i*4)%5}, centres) );
        }

        if (saveInputsForInspection)
        {
            SimplifiedIO.saveImage(traImg,"tra.tif");
            int cnt = 0;
            for (RandomAccessibleInterval<?> segImg : segImgs)
                SimplifiedIO.saveImage(segImg,"seg"+(++cnt)+".tif");
        }


        //the "job file" (provided as params) is processed and synced results are saved to disk,
        //this is all-in-one method that exits only after it is completely done
/*
        labelSync.syncAllInputsAndSaveAllToDisk(
                "/Users/ulman/devel/measures/seg1.tif", "1",
                "/Users/ulman/devel/measures/seg2.tif", "1",
                "/Users/ulman/devel/measures/seg3.tif", "1",
                "/Users/ulman/devel/measures/tra.tif",
                "0",
                "/Users/ulman/devel/measures/synced.tif" );
*/
        if (saveOutputsForInspection)
        {
            labelSync.syncAllInputsAndSaveAllToDisk(segImgs,traImg);
            System.out.println("-------------------------------------------------------");
        }

        //example of how obtain and process individual images iteratively (in contrast to the all-in-one approach):
        //needless to say, one should not change any of the parameters above while in the middle of this process
        //
        //but here the process hasn't started yet, so we can change (last minute) change it here :-)
        labelSync.wantPerLabelProcessing = true;

        //get a reference on ImagesWithOrigin object
        LabelSync<UnsignedByteType, UnsignedShortType>.ImagesWithOrigin images;
        images = labelSync.syncAllInputsAndStreamIt(labelSync.inImgs,labelSync.markerImg);
        //NB: this has only initiated the iterative syncing, no image has been processed yet

        //iteratively process synced images
        System.out.println("Caller: Start getting images");
        while (images.hasMoreElements())
        {
            LabelSync<UnsignedByteType, UnsignedShortType>.ImgWithOrigin img = images.nextElement();
/*
            //whenever one gets bored, you can stop the syncing process like this:
            if (img.markerLabel == 4)
            {
                System.out.println("C: interrupting label syncing");
                images.interruptGettingMoreElements();
                break;
            }
*/
            System.out.println( "Caller: Got image: "+img.singleLabelImg.toString()+" from source="+img.sourceNo+" with "
                    +(img.markerLabel == -1 ? "all labels synced" : ("label="+img.markerLabel)) );
        }
        System.out.println("Caller: Done getting images");
    }
}
