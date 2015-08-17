#!/bin/bash
find -name "*.java" > sources.txt
javac -d bin -cp /home/irati/iratis/share/librina/librina.jar:/home/irati/iratis/share/rinad/librinad.jar @sources.txt
jar cfm ../iratis/bin/manager manifest.mf bin
rm sources.txt