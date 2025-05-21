@echo off
REM Script to run the Gemini Python microservice locally on Windows

echo Setting up environment...

REM Check if Python is installed
where python >nul 2>nul
if %ERRORLEVEL% neq 0 (
    echo Error: Python is not installed or not in PATH.
    echo Please install Python 3.10 or higher.
    exit /b 1
)

REM Check if virtual environment exists, create if not
if not exist venv (
    echo Creating virtual environment...
    python -m venv venv
    if %ERRORLEVEL% neq 0 (
        echo Error: Failed to create virtual environment.
        exit /b 1
    )
)

REM Activate virtual environment
echo Activating virtual environment...
call venv\Scripts\activate.bat

REM Install requirements if needed
if not exist venv\Lib\site-packages\fastapi (
    echo Installing dependencies...
    pip install -r requirements.txt
    if %ERRORLEVEL% neq 0 (
        echo Error: Failed to install dependencies.
        exit /b 1
    )
)

REM Check if .env file exists
if not exist .env (
    echo Warning: .env file not found.
    echo Creating .env file from example...
    if exist .env.example (
        copy .env.example .env
        echo Please edit the .env file with your Google Cloud credentials.
        echo Press any key to continue...
        pause >nul
    ) else (
        echo Error: .env.example file not found.
        echo Please create a .env file with your Google Cloud credentials.
        exit /b 1
    )
)

REM Run the service
echo Starting Gemini Python microservice...
python run_local.py %*

REM Deactivate virtual environment when done
call venv\Scripts\deactivate.bat
