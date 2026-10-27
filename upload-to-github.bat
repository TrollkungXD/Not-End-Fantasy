@echo off
cd /d "%~dp0"
echo === Not End Fantasy - upload to GitHub ===
where git >nul 2>nul
if errorlevel 1 (
  echo Git is not installed. Download it from https://git-scm.com/download/win , install, then run this file again.
  pause
  exit /b 1
)
if not exist .git git init -q
git config core.autocrlf true
git config user.name >nul 2>nul || git config user.name "TrollkungXD"
git config user.email >nul 2>nul || git config user.email "chanathip1415@gmail.com"
git remote remove origin >nul 2>nul
git remote add origin https://github.com/TrollkungXD/Not-End-Fantasy.git
echo Downloading current GitHub history...
git fetch -q origin main
if errorlevel 1 (
  echo Could not reach the GitHub repo. A sign-in window may have been closed - run this file again.
  pause
  exit /b 1
)
git symbolic-ref HEAD refs/heads/main
git update-ref refs/heads/main origin/main
git reset -q
findstr /x /c:"upload-to-github.bat" .git\info\exclude >nul 2>nul || echo upload-to-github.bat>> .git\info\exclude
git add -A
git add -f jlayer-1.0.1.jar
git commit -q -m "Reorganize folders: assets in NotEndFantasy/usage, source in NotEndFantasy/src" -m "AppFiles finds the resource folder from the jar/class location, so NotEndFantasy.jar works when double-clicked. run.bat / run.sh and README updated for the new layout. JLayer jar and its LGPL license moved to the project root."
echo Uploading... (a GitHub sign-in window may open - sign in and allow)
git push origin main
if errorlevel 1 (
  echo Upload failed - see the message above.
) else (
  echo Done! https://github.com/TrollkungXD/Not-End-Fantasy
)
pause
