# Use an official Python runtime as a parent image
FROM python:3.10-slim

# Set the working directory in the container
WORKDIR /app

# Copy the requirements file into the container at /app
COPY requirements.txt requirements.txt

# Install any needed packages specified in requirements.txt
# --no-cache-dir: Disables the cache to reduce image size
# --trusted-host pypi.python.org: Can be useful if there are SSL issues with PyPI, often not needed
RUN pip install --no-cache-dir -r requirements.txt

# Set environment variables with default empty values
# These will be overridden by Docker run/compose environment variables
ENV MODEL_NAME="gemini-2.0-flash-001"
ENV GCP_PROJECT_ID=""
ENV GCP_LOCATION="us-central1"

# Legacy support for service account key file if needed
ENV GOOGLE_APPLICATION_CREDENTIALS=""

# Copy the rest of the application code (the 'app' directory) into the container at /app
COPY ./app /app/app

# Make port 8000 available to the world outside this container
EXPOSE 8000

# Define environment variable for the Python path if necessary (often not needed with this structure)
# ENV PYTHONPATH=/app

# Run app.main:app when the container launches
# uvicorn app.main:app --host 0.0.0.0 --port 8000
# The command should refer to the module path from where uvicorn is run (WORKDIR /app)
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
