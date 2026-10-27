@echo off
cd /d "%~dp0"
if not exist NotEndFantasy\out mkdir NotEndFantasy\out
javac -encoding UTF-8 -cp jlayer-1.0.1.jar -d NotEndFantasy\out NotEndFantasy\src\*.java && java -cp "NotEndFantasy\out;jlayer-1.0.1.jar" Main
pause
