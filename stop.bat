@echo off
cd /d "%~dp0PKS_SEM_5-main\autosalon-javafx"
rem Stop the database without removing its data volume.
docker compose down
if errorlevel 1 pause
