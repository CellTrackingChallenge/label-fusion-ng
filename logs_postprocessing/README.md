# Lines Sorting

The fusion CLI code, when operated in the CMV regime since the commit c8a3d1823167,
is aggregating log reports into "fewer" files. In other words, all the work on
individual examined combinations is logged into files of the following pattern:

```
log_*thread_*txt
```

Additionally, note that any combination is identified using a string of Ys and Ns,
followed by the underscore and a threshold value, for example `YNNYYYYYYYYYYYNN_12`.

In order to extract a complete log for a given combination, one has to collect
the necessary lines from multiple log files and sort them chronologically.
This can be achieved by using the following command:

```
grep YNNYYYYYYYYYYYNN_12 log_*thread*txt | ./lines_sorter.py > log.txt
```

where the `grep` collects the lines and the `lines_sorter.py` (available in this folder) reformats them.


# SEG values gathering

The fusion CLI code, when operated in the CMV regime since the commit c8a3d1823167
(and actually since earlier), is capable of running the CMV on selected time points.
This way, one can run multiple fusion instances on different time points of the same
video, which comes with a new necessity to collect and re-calculate the final average
SEG afterwards from the log files of the individual instances, and of the individual
(per-partes processed) combinations.

In particular, the individual log files shall contain lines of the similar form (example):

```
YNNNNNNNYYYYYYYY_3 [Thu Jun 09 01:09:34 CEST 2022 INFO] segSum 2.2733760951113173 segCnt 5 detFnCnt 2 detFpCnt 0 detTpCnt 3
```

The `segSum` and `segCnt` are the basis to an average SEG for the indicated combination,
collected over the data processed thus far to the point of time that is recorded in the
log. One may want to collect multiple such records across multiple log files, filter out
un-wanted intermediate values of `segSum` and `segCnt` and process them altogether, e.g.
with the following command:

```
cat logs*txt | ./collect_segSums.py 76 24 
```

extracts the lines matching `segCnt 76` and `segCnt 24` in the log files for every combination
reported in the files and reports on the standard output the re-calculated SEG value.
