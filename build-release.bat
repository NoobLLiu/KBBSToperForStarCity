@echo off
setlocal enabledelayedexpansion
cd /d %~dp0

echo ============================================================
echo   KBBSToperForStarCity 一键构建并发布 GitHub Release
echo   仓库: NoobLLiu/KBBSToperForStarCity
echo ============================================================
echo.

REM ---------- 1. 检查 JDK 21 ----------
java -version 2>&1 | findstr /R "21\." >nul
if errorlevel 1 (
    echo [错误] 需要 JDK 21（Gradle 8.7 + paper-api 1.21 不支持 JDK 22+）。
    echo   请先安装 Microsoft OpenJDK 21：
    echo     winget install Microsoft.OpenJDK.21
    echo   安装后确保其 java 在 PATH 中（或设置 JAVA_HOME 指向 JDK21）。
    pause
    exit /b 1
)
echo [OK] JDK 版本满足要求。

REM ---------- 2. 读取版本号（来自 build.gradle 的 version = 'x.y.z'）----------
for /f "tokens=3 delims= " %%v in ('findstr /R "version = " build.gradle') do set RAWVER=%%v
set "VERSION=%RAWVER:'=%"
if "%VERSION%"=="" (
    echo [错误] 无法从 build.gradle 读取版本号。
    pause
    exit /b 1
)
echo [OK] 版本号: %VERSION%

REM ---------- 3. 构建 Bukkit jar（core + bukkit, shadowJar）----------
echo.
echo [构建] 正在执行 gradlew :bukkit:build ...
call gradlew.bat :bukkit:build --no-daemon
if errorlevel 1 (
    echo [错误] 构建失败，请查看上面的 Gradle 输出。
    pause
    exit /b 1
)

set "JAR=bukkit\build\libs\KBBSToper-Bukkit-%VERSION%.jar"
if not exist "%JAR%" (
    echo [错误] 未找到产物: %JAR%
    echo   请确认 build.gradle 中 shadowJar 输出的 jar 名称与此一致。
    pause
    exit /b 1
)
echo [OK] 产物: %JAR%

REM ---------- 4. 发布 / 更新 Release ----------
set "TAG=v%VERSION%"
echo.
echo [发布] 处理 Release %TAG% ...

gh release view %TAG% --repo NoobLLiu/KBBSToperForStarCity >nul 2>&1
if not errorlevel 1 (
    echo   Release %TAG% 已存在，更新 jar 资产（--clobber）...
    gh release upload %TAG% "%JAR%" --repo NoobLLiu/KBBSToperForStarCity --clobber
) else (
    echo   创建 Release %TAG% 并上传 jar ...
    gh release create %TAG% "%JAR%" --repo NoobLLiu/KBBSToperForStarCity --title %TAG% --generate-notes
)
if errorlevel 1 (
    echo [错误] 发布失败。请确认：
    echo   1) 已执行 gh auth login 登录 GitHub 账号 NoobLLiu；
    echo   2) 网络可访问 github.com；
    echo   3) 对仓库有写权限。
    pause
    exit /b 1
)

echo.
echo ============================================================
echo   完成！Release 地址：
echo   https://github.com/NoobLLiu/KBBSToperForStarCity/releases/tag/%TAG%
echo ============================================================
pause
