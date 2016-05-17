#!/bin/bash
find -name "*.java" > sources.txt
javac -d bin -cp /root/irati/share/librina/librina.jar:/root/irati/share/rinad/librinad.jar @sources.txt
jar cfm /root/irati/bin/manager_java manifest.mf bin
rm sources.txt
