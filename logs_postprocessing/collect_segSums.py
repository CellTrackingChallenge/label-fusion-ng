#!/usr/bin/env python3

import sys

def contains_segSum(line: str) -> bool:
    return line.find("segSum") > -1

def extract_segSum(line: str):
    # check filename is prepended:
    # too many ':' flags that the filename was prepended
    # in which case we take only the "wanted part"
    msg = line if line.count(':') < 4 else "".join(line.split(':',5)[3:])

    terms = msg.split(' ')
    if len(terms) < 8 or terms[8].find("segSum") == -1:
        print(f"Unexpected format of {line}")
        return None,None,None

    code = terms[0]
    segSum = float(terms[9])
    segCnt = int(terms[11])
    return code,segSum,segCnt


if len(sys.argv) == 1:
    print("Provide list of segCnt values that shall be gathered per each code and")
    print("whose segSum values should be processed to obtain the overall SEG score.")

cnts = []
for a in sys.argv[1:]:
    cnts.append(int(a))

print(f"Gathering over cnts: {cnts}")


segSums = dict()
segCnts = dict()

# processing/collecting
for line in sys.stdin:
    if contains_segSum(line):
        code,segSum,segCnt = extract_segSum(line)
        #print(f"{code} : segSum = {segSum}, segCnt = {segCnt}")

        if segCnt in cnts:
            if code not in segSums:
                segSums[code] = 0.0
                segCnts[code] = 0

            segSums[code] += segSum
            segCnts[code] += segCnt

# reporting
bestSEG = 0.0
bestCode = ""
for code in sorted(segSums.keys()):
    SEG = segSums[code] / segCnts[code]
    print(f"{code} Final avg SEG = {SEG} obtained over {segCnts[code]} segments")
    if SEG > bestSEG:
        bestSEG = SEG
        bestCode = code

print(f"Best SEG achieved {bestSEG} for combination {bestCode}")