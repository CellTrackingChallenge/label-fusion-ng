package de.mpicbg.ulman.fusion;

import de.mpicbg.ulman.fusion.ng.LabelSync;
import de.mpicbg.ulman.fusion.ng.LabelSync.nameFormatTags;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import org.scijava.Context;
import org.scijava.log.LogService;

public class testLabelSyncing {
    public static void main(String[] args) {
        final LogService myLog = new Context(LogService.class).getService(LogService.class);
        //myLog.setLevel(LogService.TRACE);

        //create the label sync, for the last param decide
        //if perLabel (true) or perImage (false) regime
        LabelSync<UnsignedShortType,UnsignedShortType> labelSync = new LabelSync<>(myLog);

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

        //the "job file" (provided as params) is processed and synced results are saved to disk,
        //this is all-in-one method that exits only after it is completely done
        labelSync.syncAllInputsAndSaveAllToDisk(
                "/Users/ulman/devel/measures/seg1.tif", "1",
                "/Users/ulman/devel/measures/seg2.tif", "1",
                "/Users/ulman/devel/measures/seg3.tif", "1",
                "/Users/ulman/devel/measures/tra.tif",
                "0",
                "/Users/ulman/devel/measures/synced.tif" );

        System.out.println("-------------------------------------------------------");

        //example of how obtain and process individual images iteratively (in contrast to the all-in-one approach):
        //needless to say, one should not change any of the parameters above while in the middle of this process
        //
        //get a reference on ImagesWithOrigin object
        LabelSync<UnsignedShortType, UnsignedShortType>.ImagesWithOrigin images;
        images = labelSync.syncAllInputsAndStreamIt(labelSync.inImgs,labelSync.markerImg);
        //NB: this has only initiated the iterative syncing, no image has been processed yet

        //iteratively process synced images
        System.out.println("Caller: Start getting images");
        while (images.hasMoreElements())
        {
            LabelSync<UnsignedShortType, UnsignedShortType>.ImgWithOrigin img = images.nextElement();
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
