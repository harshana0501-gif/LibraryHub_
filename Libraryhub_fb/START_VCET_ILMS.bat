@echo off
title VCET ILMS - Scenario 4 (Reg No: 34)
echo Starting VCET ILMS Java Backend (:8080) and Frontend (:3000)...

start "VCET ILMS Backend :8080" cmd /k "cd /d %~dp0backend\src && javac -cp ".;..\lib\*" com\vcet\library\LibraryApiServer.java && java -cp ".;..\lib\*" com.vcet.library.LibraryApiServer"

timeout /t 2 /nobreak >nul

start "VCET ILMS Frontend :3000" cmd /k "cd /d %~dp0frontend && npx serve -s . -l 3000"

timeout /t 2 /nobreak >nul

start http://localhost:3000
echo Done! Both Backend (:8080) and Frontend (:3000) are launching.
