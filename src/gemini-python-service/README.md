# Gemini Python Microservice

This microservice provides an API for document classification, verification, and information extraction using Google's Gemini AI models.

## Features

- Document classification (Sales Quote, Proforma Invoice, Job Consumption)
- Document verification against ERP data
- Extraction of key identifiers from documents
- Combined classification and verification in a single step

## Running the Service

### Option 1: Running Locally

#### Prerequisites

- Python 3.10 or higher
- Google Cloud Platform account with Gemini API access
- Service account with appropriate permissions

#### Setup

1. Clone the repository and navigate to the `gemini-python-service` directory:

```bash
cd src/gemini-python-service
```

2. Create a virtual environment and activate it:

```bash
python -m venv venv
source venv/bin/activate  # On Windows: venv\Scripts\activate
```

3. Install the required dependencies:

```bash
pip install -r requirements.txt
```

4. Create a `.env` file from the example:

```bash
cp .env.example .env
```

5. Edit the `.env` file with your Google Cloud credentials and configuration.

#### Running the Service

Run the service using the provided script:

```bash
python run_local.py
```

This will start the service on http://127.0.0.1:8000.

You can customize the host and port:

```bash
python run_local.py --host 0.0.0.0 --port 8080
```

### Option 2: Running with Docker

#### Prerequisites

- Docker installed on your system

#### Building the Docker Image

```bash
docker build -t gemini-python-service .
```

#### Running the Docker Container

```bash
docker run -p 8000:8000 \
  -e GCP_PROJECT_ID=your-gcp-project-id \
  -e GCP_LOCATION=us-central1 \
  -e MODEL_NAME=gemini-2.0-flash-001 \
  -e GOOGLE_SERVICE_ACCOUNT_TYPE=service_account \
  -e GOOGLE_SERVICE_ACCOUNT_PROJECT_ID=your-gcp-project-id \
  -e GOOGLE_SERVICE_ACCOUNT_PRIVATE_KEY_ID=your-private-key-id \
  -e GOOGLE_SERVICE_ACCOUNT_PRIVATE_KEY="-----BEGIN PRIVATE KEY-----\nYour private key content here\n-----END PRIVATE KEY-----\n" \
  -e GOOGLE_SERVICE_ACCOUNT_CLIENT_EMAIL=your-service-account@your-project.iam.gserviceaccount.com \
  -e GOOGLE_SERVICE_ACCOUNT_CLIENT_ID=your-client-id \
  -e GOOGLE_SERVICE_ACCOUNT_AUTH_URI=https://accounts.google.com/o/oauth2/auth \
  -e GOOGLE_SERVICE_ACCOUNT_TOKEN_URI=https://oauth2.googleapis.com/token \
  -e GOOGLE_SERVICE_ACCOUNT_AUTH_PROVIDER_X509_CERT_URL=https://www.googleapis.com/oauth2/v1/certs \
  -e GOOGLE_SERVICE_ACCOUNT_CLIENT_X509_CERT_URL=https://www.googleapis.com/robot/v1/metadata/x509/your-service-account%40your-project.iam.gserviceaccount.com \
  -e GOOGLE_SERVICE_ACCOUNT_UNIVERSE_DOMAIN=googleapis.com \
  gemini-python-service
```

Alternatively, you can use Docker Compose as defined in the project's docker-compose.yml file.

## API Endpoints

The service provides the following endpoints:

- `POST /extract_identifiers`: Extract key identifiers from a document
- `POST /verify_document`: Verify a document against ERP data
- `POST /classify_document`: Classify a document type
- `POST /classify_and_verify`: Classify and verify a document in one step
- `GET /health`: Check the health of the service

For detailed API documentation, visit the Swagger UI at http://localhost:8000/docs when the service is running.

## Troubleshooting

### Authentication Issues

If you encounter authentication issues, check:

1. Your service account has the necessary permissions for Vertex AI and Gemini models
2. The service account credentials in your `.env` file are correct
3. The project ID matches the service account's project

### Model Access Issues

If you encounter issues accessing the Gemini models:

1. Ensure your project has the Vertex AI API enabled
2. Verify you have access to the Gemini models in your region
3. Check that the model name in your configuration is correct

### Health Check

Use the `/health` endpoint to check the status of the service and diagnose issues:

```bash
curl http://localhost:8000/health
```
