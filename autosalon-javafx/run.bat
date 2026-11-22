@echo off
rem Start PostgreSQL in Docker, then the JavaFX desktop app. Requires JDK 21+.
cd /d "%~dp0"
where docker >nul 2>&1
if errorlevel 1 (
    echo Docker was not found. Install and start Docker Desktop.
    pause
    exit /b 1
)
where java >nul 2>&1
if errorlevel 1 (
    echo Java was not found. Install JDK 21 or newer and add it to PATH.
    pause
    exit /b 1
)
echo Starting Docker Desktop...
docker desktop start
if errorlevel 1 (
    echo Docker Desktop startup failed. Open Docker Desktop and check its status.
    pause
    exit /b 1
)
echo Starting PostgreSQL in Docker...
docker compose up -d --wait --wait-timeout 180
if errorlevel 1 (
    echo Database startup failed. Check that Docker Desktop is running.
    echo For details, run: docker compose logs --tail 100
    pause
    exit /b 1
)
echo Starting the application. The first run may download Maven and dependencies.
call mvnw.cmd -q javafx:run
if errorlevel 1 pause
