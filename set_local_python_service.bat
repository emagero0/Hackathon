@echo off
REM Script to set the environment variable for the local Python service

echo Setting environment variable for local Python service...
set LLM_PYTHON_SERVICE_BASEURL=http://localhost:8000

echo Environment variable set:
echo LLM_PYTHON_SERVICE_BASEURL=%LLM_PYTHON_SERVICE_BASEURL%

echo.
echo Now you can run the Java backend with:
echo cd src\backend
echo .\mvnw spring-boot:run

echo.
echo Press any key to exit...
pause >nul
