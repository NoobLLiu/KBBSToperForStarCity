@echo off
setlocal enabledelayedexpansion
cd /d "%~dp0"

echo ============================================================
echo   KBBSToperForStarCity - one-click build and GitHub release
echo   Repo: NoobLLiu/KBBSToperForStarCity
echo ============================================================
echo(

REM ---------- 1. Check JDK 21 ----------
java -version 2>&1 | findstr /R "21\." >nul
if errorlevel 1 (
    echo [ERROR] JDK 21 is required (Gradle 8.7 + paper-api 1.21 do not support JDK 22+).
    echo   Install Microsoft OpenJDK 21:
    echo     winget install Microsoft.OpenJDK.21
    echo   Make sure its java is on PATH (or set JAVA_HOME to JDK21).
    pause
    exit /b 1
)
echo [OK] JDK version is acceptable.

REM ---------- 2. Read version from build.gradle (version = 'x.y.z') ----------
for /f "usebackq tokens=3" %%v in (`findstr /C:"version = " build.gradle`) do (
    set "RAWVER=%%v"
    goto :gotver
)
:gotver
set "VERSION=%RAWVER:'=%"
if "%VERSION%"=="" (
    echo [ERROR] Could not read version from build.gradle.
    pause
    exit /b 1
)
echo [OK] Version: %VERSION%

REM ---------- 3. Build Bukkit jar (core + bukkit, shadowJar) ----------
echo(
echo [BUILD] Running gradlew :bukkit:build ...
call gradlew.bat :bukkit:build --no-daemon
if errorlevel 1 (
    echo [ERROR] Build failed. See Gradle output above.
    pause
    exit /b 1
)

set "JAR=bukkit\build\libs\KBBSToper-Bukkit-%VERSION%.jar"
if not exist "%JAR%" (
    echo [ERROR] Artifact not found: %JAR%
    echo   Confirm the shadowJar output name in build.gradle matches this.
    pause
    exit /b 1
)
echo [OK] Artifact: %JAR%

REM ---------- 4. Create / update Release ----------
set "TAG=v%VERSION%"
echo(
echo [RELEASE] Handling Release %TAG% ...

gh release view %TAG% --repo NoobLLiu/KBBSToperForStarCity >nul 2>&1
if not errorlevel 1 (
    echo   Release %TAG% already exists, updating jar asset (--clobber)...
    gh release upload %TAG% "%JAR%" --repo NoobLLiu/KBBSToperForStarCity --clobber
) else (
    echo   Creating Release %TAG% and uploading jar ...
    gh release create %TAG% "%JAR%" --repo NoobLLiu/KBBSToperForStarCity --title %TAG% --generate-notes
)
if errorlevel 1 (
    echo [ERROR] Release failed. Check:
    echo   1) gh auth login done with GitHub account NoobLLiu;
    echo   2) network can reach github.com;
    echo   3) write permission on the repo.
    pause
    exit /b 1
)

echo(
echo ============================================================
echo   DONE! Release URL:
echo   https://github.com/NoobLLiu/KBBSToperForStarCity/releases/tag/%TAG%
echo ============================================================
pause
