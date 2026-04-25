@echo off
adb -s R3CN203BDKN forward tcp:5277 tcp:5277
start "" "C:\Users\daotr\AppData\Local\Android\Sdk\extras\google\auto\desktop-head-unit.exe" --config "C:\Users\daotr\AppData\Local\Android\Sdk\extras\google\auto\config\vf3_720p.ini"