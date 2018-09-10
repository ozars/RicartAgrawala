#!/usr/bin/python
import sys
import re
import datetime

def pout(ind, ln):
    print "[{}]{}".format(sys.argv[ind+1], ln)

farr = [open(fn, 'r') for fn in sys.argv[1:]]
print farr
nextline = [None] * len(sys.argv)
timestamp = [None] * len(sys.argv)
while len(farr) > 0:
    for i in xrange(len(farr)-1, -1, -1):
        cont = True
        while cont and nextline[i] == None:
            nextline[i] = farr[i].readline()
            if nextline[i] == "":
                farr[i].close()
                del farr[i]
                del nextline[i]
                del timestamp[i]
                cont = False
            else:
                nextline[i] = nextline[i].strip()
                timestamp[i] = re.search(r"\[(\d{2}:\d{2}:\d{2}\.\d{3})\]", nextline[i])
                if timestamp[i] == None:
                    pout(i, nextline[i])
                    nextline[i] = None
                else:
                    timestamp[i] = timestamp[i].group(1)
    best = None
    for i in xrange(len(farr)):
        if best == None or timestamp[best] > timestamp[i]:
            best = i
    pout(best, nextline[best])
    nextline[best] = None
