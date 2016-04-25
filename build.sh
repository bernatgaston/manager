#!/bin/bash
find -name "*.java" > sources.txt
javac -d bin -cp /home/irati/irati/share/librina/librina.jar:/home/irati/irati/share/rinad/librinad.jar @sources.txt
jar cfm ../irati/bin/manager_java manifest.mf bin
rm sources.txt
