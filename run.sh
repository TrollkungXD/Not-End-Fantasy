#!/bin/sh
cd "$(dirname "$0")"
mkdir -p NotEndFantasy/out && javac -encoding UTF-8 -cp jlayer-1.0.1.jar -d NotEndFantasy/out NotEndFantasy/src/*.java && java -cp "NotEndFantasy/out:jlayer-1.0.1.jar" Main
