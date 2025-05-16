# Environment Setup Guide

This document provides instructions for setting up the required environment variables for the application.

## Required Environment Variables

The following environment variables need to be set before running the application:

### Database Configuration
- `DB_USERNAME`: Database username (defaults to "root" if not set)
- `DB_PASSWORD`: Database password

### Business Central API Configuration
- `DYNAMICS_BC_USERNAME`: Business Central API username (defaults to "webservice" if not set)
- `DYNAMICS_BC_API_KEY`: Business Central API key

### RabbitMQ Configuration (Optional)
- `RABBITMQ_USERNAME`: RabbitMQ username (defaults to "guest" if not set)
- `RABBITMQ_PASSWORD`: RabbitMQ password (defaults to "guest" if not set)

## Setting Environment Variables

### For Windows

```cmd
set DB_USERNAME=your_db_username
set DB_PASSWORD=your_db_password
set DYNAMICS_BC_USERNAME=your_bc_username
set DYNAMICS_BC_API_KEY=your_bc_api_key
```

### For Linux/macOS

```bash
export DB_USERNAME=your_db_username
export DB_PASSWORD=your_db_password
export DYNAMICS_BC_USERNAME=your_bc_username
export DYNAMICS_BC_API_KEY=your_bc_api_key
```

### For Docker

Add these environment variables to your docker-compose.yml file:

```yaml
services:
  backend:
    environment:
      - DB_USERNAME=your_db_username
      - DB_PASSWORD=your_db_password
      - DYNAMICS_BC_USERNAME=your_bc_username
      - DYNAMICS_BC_API_KEY=your_bc_api_key
```

## Google Cloud Service Account Setup

For the Gemini Python service, you have two options for authentication:

### Option 1: Using Environment Variables (Recommended)

1. Create a service account in Google Cloud Console
2. Generate a JSON key for the service account
3. Add the service account credentials to your `.env` file using the following format:

```
# Google Cloud Project Configuration
GCP_PROJECT_ID=your-project-id
GCP_LOCATION=us-central1

# Google Cloud Service Account Credentials
GOOGLE_SERVICE_ACCOUNT_TYPE=service_account
GOOGLE_SERVICE_ACCOUNT_PROJECT_ID=your-project-id
GOOGLE_SERVICE_ACCOUNT_PRIVATE_KEY_ID=your-private-key-id
GOOGLE_SERVICE_ACCOUNT_PRIVATE_KEY="-----BEGIN PRIVATE KEY-----\nYOUR_PRIVATE_KEY_HERE\n-----END PRIVATE KEY-----\n"
GOOGLE_SERVICE_ACCOUNT_CLIENT_EMAIL=your-service-account@your-project-id.iam.gserviceaccount.com
GOOGLE_SERVICE_ACCOUNT_CLIENT_ID=your-client-id
GOOGLE_SERVICE_ACCOUNT_AUTH_URI=https://accounts.google.com/o/oauth2/auth
GOOGLE_SERVICE_ACCOUNT_TOKEN_URI=https://oauth2.googleapis.com/token
GOOGLE_SERVICE_ACCOUNT_AUTH_PROVIDER_X509_CERT_URL=https://www.googleapis.com/oauth2/v1/certs
GOOGLE_SERVICE_ACCOUNT_CLIENT_X509_CERT_URL=https://www.googleapis.com/robot/v1/metadata/x509/your-service-account%40your-project-id.iam.gserviceaccount.com
GOOGLE_SERVICE_ACCOUNT_UNIVERSE_DOMAIN=googleapis.com
```

### Option 2: Using a Service Account Key File (Legacy)

1. Create a service account in Google Cloud Console
2. Generate a JSON key for the service account
3. Save the key as `service-account-key.json` in the `src/gemini-python-service/` directory
4. Set the `GOOGLE_APPLICATION_CREDENTIALS` environment variable in the `.env` file to point to this file

See `src/gemini-python-service/README_SERVICE_ACCOUNT.md` for more details.
