@echo off
chcp 65001 >nul
setlocal EnableExtensions
cd /d "%~dp0"

set "SCRIPT=%~dp0lottery-python\src\main\resources\python\install_deps.py"
if not exist "%SCRIPT%" (
    echo [错误] 找不到安装脚本:
    echo        %SCRIPT%
    pause
    exit /b 1
)

echo.
echo ================================================================
echo   双色球 Python 依赖一键安装
echo ================================================================
echo.

set "PY="
where py >nul 2>&1
if %errorlevel%==0 (
    py -3 -c "import sys" >nul 2>&1
    if %errorlevel%==0 set "PY=py -3"
)

if not defined PY (
    where python >nul 2>&1
    if %errorlevel%==0 (
        python -c "import sys" >nul 2>&1
        if %errorlevel%==0 set "PY=python"
    )
)

if not defined PY (
    echo [错误] 未找到可用的 Python。
    echo        请安装 Python 3.9 ~ 3.12，安装时勾选 "Add python.exe to PATH"。
    echo        下载: https://www.python.org/downloads/
    echo.
    pause
    exit /b 1
)

echo [解释器] %PY%
echo.

%PY% "%SCRIPT%" --add-path %*
set "ERR=%errorlevel%"

echo.
if %ERR%==0 (
    echo 安装完成。若刚写入 PATH，请关闭本窗口后重新打开终端 / IDE。
) else (
    echo 安装未完全成功，退出码 %ERR%。请向上滚动查看错误信息。
)
echo.
pause
exit /b %ERR%
