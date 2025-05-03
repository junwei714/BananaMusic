@echo off
cd %~dp0
echo Creating modified version of AudioPlayerHelper.java...

:: Create a new file with all contents up to the first isNetworkAvailable method (keeping it)
powershell -Command "Get-Content 'app\src\main\java\my\edu\utar\bananamusic\utils\AudioPlayerHelper.java' | Select-Object -First 4000 > 'AudioPlayerHelper_fixed.java'"

:: Append content from after the second method to the end
powershell -Command "Get-Content 'app\src\main\java\my\edu\utar\bananamusic\utils\AudioPlayerHelper.java' | Select-Object -Skip 4035 >> 'AudioPlayerHelper_fixed.java'"

:: Replace the original with the fixed version
copy AudioPlayerHelper_fixed.java app\src\main\java\my\edu\utar\bananamusic\utils\AudioPlayerHelper.java /Y

:: Clean up
del AudioPlayerHelper_fixed.java

echo Fixed file has been saved back to the original location. 