#!/bin/bash
# Script to run the Gemini Python microservice locally on Unix/Linux/Mac

set -e

echo "Setting up environment..."

# Check if Python is installed
if ! command -v python3 &> /dev/null; then
    echo "Error: Python 3 is not installed or not in PATH."
    echo "Please install Python 3.10 or higher."
    exit 1
fi

# Check if virtual environment exists, create if not
if [ ! -d "venv" ]; then
    echo "Creating virtual environment..."
    python3 -m venv venv
fi

# Activate virtual environment
echo "Activating virtual environment..."
source venv/bin/activate

# Install requirements if needed
if [ ! -d "venv/lib/python3.10/site-packages/fastapi" ]; then
    echo "Installing dependencies..."
    pip install -r requirements.txt
fi

# Check if .env file exists
if [ ! -f ".env" ]; then
    echo "Warning: .env file not found."
    echo "Creating .env file from example..."
    if [ -f ".env.example" ]; then
        cp .env.example .env
        echo "Please edit the .env file with your Google Cloud credentials."
        echo "Press Enter to continue..."
        read
    else
        echo "Error: .env.example file not found."
        echo "Please create a .env file with your Google Cloud credentials."
        exit 1
    fi
fi

# Make the run_local.py script executable
chmod +x run_local.py

# Run the service
echo "Starting Gemini Python microservice..."
./run_local.py "$@"

# Deactivate virtual environment when done
deactivate
