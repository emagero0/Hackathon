#!/usr/bin/env python3
"""
Script to run the Gemini Python microservice locally.
This script sets up the necessary environment and runs the FastAPI application.
"""

import os
import sys
import subprocess
import argparse
from pathlib import Path

def setup_environment():
    """Set up the environment variables needed for the service."""
    # Get the absolute path to the .env file
    env_file = Path(__file__).parent / '.env'
    
    if not env_file.exists():
        print(f"Warning: .env file not found at {env_file}")
        print("You need to create a .env file with the required configuration.")
        print("See .env.example for the required variables.")
        return False
    
    # Read the .env file and set environment variables
    with open(env_file, 'r') as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith('#'):
                continue
            key, value = line.split('=', 1)
            os.environ[key] = value
    
    # Check for required environment variables
    required_vars = ['GCP_PROJECT_ID', 'GCP_LOCATION', 'MODEL_NAME']
    missing_vars = [var for var in required_vars if not os.environ.get(var)]
    
    if missing_vars:
        print(f"Error: Missing required environment variables: {', '.join(missing_vars)}")
        print("Please add them to your .env file.")
        return False
    
    return True

def run_service(host='127.0.0.1', port=8000, reload=True):
    """Run the FastAPI service using uvicorn."""
    cmd = [
        "uvicorn", 
        "app.main:app", 
        "--host", host, 
        "--port", str(port)
    ]
    
    if reload:
        cmd.append("--reload")
    
    print(f"Starting Gemini Python microservice on http://{host}:{port}")
    subprocess.run(cmd)

def main():
    parser = argparse.ArgumentParser(description='Run the Gemini Python microservice locally')
    parser.add_argument('--host', default='127.0.0.1', help='Host to bind the server to')
    parser.add_argument('--port', type=int, default=8000, help='Port to bind the server to')
    parser.add_argument('--no-reload', action='store_true', help='Disable auto-reload on code changes')
    
    args = parser.parse_args()
    
    if not setup_environment():
        sys.exit(1)
    
    run_service(host=args.host, port=args.port, reload=not args.no_reload)

if __name__ == '__main__':
    main()
