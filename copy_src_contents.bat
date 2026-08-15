@echo off
setlocal enabledelayedexpansion

set OUTPUT_FILE=gear-filter-codebase.txt
set SRC_DIR=src/main/java/com

REM Clear or create the output file
type nul > %OUTPUT_FILE%

REM Loop through all files in src folder and subfolders
for /R "%SRC_DIR%" %%f in (*) do (
    echo ======================================== >> %OUTPUT_FILE%
    echo FILE: %%f >> %OUTPUT_FILE%
    echo ======================================== >> %OUTPUT_FILE%
    echo. >> %OUTPUT_FILE%
    type "%%f" >> %OUTPUT_FILE%
    echo. >> %OUTPUT_FILE%
    echo. >> %OUTPUT_FILE%
)

echo All files copied to %OUTPUT_FILE%
pause
