package de.mpicbg.ulman.fusion;

import java.io.*;
import java.util.Map;
import java.util.Scanner;

public class TraToSeg
{
    public static
    String readGTMapping(final String inputPath,
                         final int[] TPandSlice,
                         final Map<Integer,Integer> traToSegMap)
    {
        traToSegMap.clear();

        try ( FileReader fr = new FileReader(inputPath) )
        {
            Scanner s = new Scanner(new BufferedReader(fr));
            TPandSlice[0] = s.nextInt();            //TP
            s.nextLine();                           //rewind to the beginning of the next (2nd) line
            String segGtImgPath = s.nextLine();     //seg GT image path
            TPandSlice[1] = s.nextInt();            //slice No.
            while (s.hasNext())                     //tra -> seg mappings (multiple, one per line)
                traToSegMap.put( s.nextInt(), s.nextInt() );
            return segGtImgPath;
        } catch (IOException e) {
            //System.err.println("Error reading "+inputPath);
            throw new UnsupportedOperationException("Error reading "+inputPath,e);
        }
    }

    public static
    String getMappingPath(final String gtTraPath)
    {
        return gtTraPath.replace(".tif",".traSegMapping.txt");
    }
}
