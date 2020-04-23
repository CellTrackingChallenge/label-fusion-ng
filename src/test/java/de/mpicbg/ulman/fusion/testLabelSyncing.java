package de.mpicbg.ulman.fusion;

import de.mpicbg.ulman.fusion.ng.LabelSyncFeeder;
import de.mpicbg.ulman.fusion.ng.LabelSyncFeeder.nameFormatTags;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import org.scijava.Context;
import org.scijava.log.LogService;

public class testLabelSyncing {
    public static void main(String[] args) {
        final LogService myLog = new Context(LogService.class).getService(LogService.class);
        //myLog.setLevel(LogService.TRACE);

        //create the sync feeder, for the last param decide
        //if perLabel (true) or perImage (false) regime
        LabelSyncFeeder<UnsignedShortType,UnsignedShortType> feeder
                = new LabelSyncFeeder<>(myLog,false);

        //tell timepoint and start it,
        //this needs to be updated every time prior *ToDisk() calls
        feeder.currentTime = 99;

        //optional param: if not set, all labels are used
        feeder.syncLabels(2,5);

        //optional param: if not set, default overlap ratio of 0.5 is used
        feeder.setMinOverlapOverTRA(0.9f);

        //optional param: if perImage regime, reset the output filename patter
        if (!feeder.wantPerLabelProcessing)
        {
            //filename string, may (should...) include path,
            //must include %d placeholders, or %0Xd for 0-padded X-wide numbers
            feeder.outputFilenameFormat = "source%d__time%03d.tif";
            //
            //define the semantics of the %d placeholders, in the left->right order
            feeder.outputFilenameOrder = new nameFormatTags[] { nameFormatTags.source, nameFormatTags.time};
        }

        //the "job file" (provided as params) is processed and synced results are saved to disk
        feeder.syncAllInputsAndSaveAllToDisk(
                "/Users/ulman/devel/measures/seg1.tif", "1",
                "/Users/ulman/devel/measures/seg2.tif", "1",
                "/Users/ulman/devel/measures/seg3.tif", "1",
                "/Users/ulman/devel/measures/tra.tif",
                "0",
                "/Users/ulman/devel/measures/synced.tif" );

        System.out.println("-------------------------------------------------------");

        //example of how obtain and process individual images iteratively:
        //
        //get a reference on ImagesWithOrigin object
        LabelSyncFeeder<UnsignedShortType, UnsignedShortType>.ImagesWithOrigin images;
        images = feeder.syncAllInputsAndStreamIt(feeder.inImgs,feeder.markerImg);

        //iteratively process synced images
        System.out.println("C: start getting images");
        while (images.hasMoreElements())
        {
            LabelSyncFeeder<UnsignedShortType, UnsignedShortType>.ImgWithOrigin img = images.nextElement();
/*
            //whenever one gets bored, you can stop the syncing process like this:
            if (img.markerLabel == 4)
            {
                System.out.println("C: interrupting label syncing");
                images.interruptGettingMoreElements();
                break;
            }
*/
            System.out.println("C: got image: "+img.singleLabelImg.toString()+" from source="+img.sourceNo+" with label="+img.markerLabel);
        }
        System.out.println("C: done getting images");
    }
}
