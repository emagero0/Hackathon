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

# Copy the .env file.
# IMPORTANT: For production, consider managing secrets via Docker secrets or environment variables
# passed during 'docker run' or by the orchestration platform, rather than baking .env into the image.
# For simplicity in this setup, we'll copy it. User should be aware of security implications.
COPY .env .env

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
