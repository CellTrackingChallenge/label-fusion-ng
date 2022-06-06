The fusion CLI code, when operated in the CMV regime since the commit c8a3d1823167,
is aggregating log reports into "fewer" files. In other words, all the work on
individual examined combinations is logged into files of the following pattern:

```
log_*thread_*txt
```

Additionally, note that any combination is identified using a string of Ys and Ns,
followed by underscore and threshold value, for example `YNNYYYYYYYYYYYNN_12`.

In order to extract a complete log for a given combination, one has to collect
the necessary lines from multiple log files and sort them chronologically.
This can be achieved by using the following command:

```
grep YNNYYYYYYYYYYYNN_12 log_*thread*txt | ./lines_sorter.py > log.txt
```

where the grep collects the lines and the `lines_sorter.py` reformats them.
