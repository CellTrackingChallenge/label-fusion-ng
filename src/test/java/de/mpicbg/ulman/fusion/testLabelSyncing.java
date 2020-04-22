package de.mpicbg.ulman.fusion;

import de.mpicbg.ulman.fusion.ng.LabelSyncFeeder;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import org.scijava.Context;
import org.scijava.log.LogService;

public class testLabelSyncing {
    public static void main(String[] args) {
        final LogService myLog = new Context(LogService.class).getService(LogService.class);
        myLog.setLevel(LogService.TRACE);

        //create the sync feeder
        LabelSyncFeeder<UnsignedShortType,UnsignedShortType> feeder
                = new LabelSyncFeeder<>(myLog,false);

        //tell timepoint and start it
        feeder.currentTime = 99;
        LabelSyncFeeder<UnsignedShortType, UnsignedShortType>.ImagesWithOrigin images
                = feeder.syncAllInputsAndStreamIt();

        //iteratively process synced images
        System.out.println("C: start getting images");
        while (images.hasMoreElements())
        {
            LabelSyncFeeder<UnsignedShortType, UnsignedShortType>.ImgWithOrigin img = images.nextElement();
            if (img.markerLabel == 3)
            {
                System.out.println("C: interrupting label syncing");
                images.interruptGettingMoreElements();
                break;
            }
            System.out.println("C: got image: "+img.singleLabelImg.toString()+" from source="+img.sourceNo+" with label="+img.markerLabel);
            try {
                Thread.sleep((int)(Math.random()*5000));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println("C: just finished processing it...");
        }
        System.out.println("C: done getting images");
    }
}
