@echo off
chcp 65001 >nul
cd /d "%~dp0"
echo.
echo  ==== EisNacht Sortieren wird gebaut ====
echo  Beim ersten Mal dauert das ein paar Minuten (laedt Minecraft und Fabric).
echo.
call gradlew.bat build
if errorlevel 1 (
  echo.
  echo  FEHLER! Mach einen Screenshot von diesem Fenster und schick ihn Claude.
  pause
  exit /b 1
)
copy /Y "build\libs\eisnacht-sort-1.0.0.jar" "EisNacht-Sortieren-1.0.0.jar" >nul
echo.
echo  FERTIG! Deine Mod: EisNacht-Sortieren-1.0.0.jar
echo  Die kommt in deinen mods-Ordner.
explorer "%~dp0"
pause
