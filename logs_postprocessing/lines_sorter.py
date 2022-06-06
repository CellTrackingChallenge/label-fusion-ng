#!/usr/bin/env python3

import sys

def extractTime(line: str):
    tl = line.find('[')
    tr = line.find(']')

    if tl > -1 and tr > -1:
        return line[0:tl], line[tl+1:tr], line[tr+1:]

    return "NA","time-not-found",line


time_sorted_lines = dict()

for line in sys.stdin:
    source,time,msg = extractTime(line)
    if time in time_sorted_lines:
        time_sorted_lines[time].append(msg)
    else:
        time_sorted_lines[time] = [msg]


for time in sorted(time_sorted_lines.keys()):
    for msg in time_sorted_lines[time]:
        print(f"[{time}] {msg}",end="")
