# CI/CD Setup Guide

This document provides instructions for setting up CI/CD pipelines for this project.

## Environment Variables

The application requires several environment variables to function properly. These should be configured as secrets in your CI/CD platform.

### Required Environment Variables

- `DB_PASSWORD`: MySQL database password
- `DYNAMICS_BC_USERNAME`: Business Central API username
- `DYNAMICS_BC_API_KEY`: Business Central API key
- `GEMINI_API_KEY`: Google Gemini API key

### Optional Environment Variables (with defaults)

- `DB_USERNAME`: Database username (default: "root")
- `RABBITMQ_USERNAME`: RabbitMQ username (default: "guest")
- `RABBITMQ_PASSWORD`: RabbitMQ password (default: "guest")
- `GEMINI_MODEL_NAME`: Gemini model name (default: "gemini-2.0-flash-001")

## Google Cloud Service Account Authentication

The Gemini Python service requires authentication with Google Cloud. There are two ways to provide this authentication:

### Option 1: Using Environment Variables (Recommended)

You can provide the service account credentials directly as environment variables in your CI/CD platform. This is the recommended approach as it avoids creating temporary files with sensitive information.

Required environment variables:
- `GOOGLE_SERVICE_ACCOUNT_TYPE`: Usually "service_account"
- `GOOGLE_SERVICE_ACCOUNT_PROJECT_ID`: Your GCP project ID
- `GOOGLE_SERVICE_ACCOUNT_PRIVATE_KEY_ID`: The private key ID
- `GOOGLE_SERVICE_ACCOUNT_PRIVATE_KEY`: The private key (including newlines)
- `GOOGLE_SERVICE_ACCOUNT_CLIENT_EMAIL`: The service account email
- `GOOGLE_SERVICE_ACCOUNT_CLIENT_ID`: The client ID
- `GOOGLE_SERVICE_ACCOUNT_AUTH_URI`: The auth URI (usually "https://accounts.google.com/o/oauth2/auth")
- `GOOGLE_SERVICE_ACCOUNT_TOKEN_URI`: The token URI (usually "https://oauth2.googleapis.com/token")
- `GOOGLE_SERVICE_ACCOUNT_AUTH_PROVIDER_X509_CERT_URL`: The auth provider cert URL
- `GOOGLE_SERVICE_ACCOUNT_CLIENT_X509_CERT_URL`: The client cert URL
- `GOOGLE_SERVICE_ACCOUNT_UNIVERSE_DOMAIN`: The universe domain (usually "googleapis.com")

### Option 2: Using a Service Account Key File (Legacy)

Alternatively, you can store the service account key as a secret in your CI/CD platform and inject it as a file during the build process.

## GitHub Actions Example

Here's an example GitHub Actions workflow:

```yaml
name: Build and Deploy

on:
  push:
    branches: [ main ]

jobs:
  build-and-deploy:
    runs-on: ubuntu-latest

    steps:
    - uses: actions/checkout@v2

    - name: Set up Docker Buildx
      uses: docker/setup-buildx-action@v1

    - name: Login to Docker Hub
      uses: docker/login-action@v1
      with:
        username: ${{ secrets.DOCKER_HUB_USERNAME }}
        password: ${{ secrets.DOCKER_HUB_TOKEN }}

    # Option 1: Using environment variables for Google Cloud authentication (recommended)
    # These will be passed to the container during build and runtime
    # The service account key JSON should be stored as a secret in GitHub

    - name: Build and push
      uses: docker/build-push-action@v2
      with:
        context: .
        push: true
        tags: yourusername/erp-backend:latest
        build-args: |
          DB_PASSWORD=${{ secrets.DB_PASSWORD }}
          DYNAMICS_BC_USERNAME=${{ secrets.DYNAMICS_BC_USERNAME }}
          DYNAMICS_BC_API_KEY=${{ secrets.DYNAMICS_BC_API_KEY }}
          GCP_PROJECT_ID=${{ secrets.GCP_PROJECT_ID }}
          GCP_LOCATION=${{ secrets.GCP_LOCATION }}
          GEMINI_MODEL_NAME=${{ secrets.GEMINI_MODEL_NAME }}
          GOOGLE_SERVICE_ACCOUNT_TYPE=${{ secrets.GOOGLE_SERVICE_ACCOUNT_TYPE }}
          GOOGLE_SERVICE_ACCOUNT_PROJECT_ID=${{ secrets.GOOGLE_SERVICE_ACCOUNT_PROJECT_ID }}
          GOOGLE_SERVICE_ACCOUNT_PRIVATE_KEY_ID=${{ secrets.GOOGLE_SERVICE_ACCOUNT_PRIVATE_KEY_ID }}
          GOOGLE_SERVICE_ACCOUNT_PRIVATE_KEY=${{ secrets.GOOGLE_SERVICE_ACCOUNT_PRIVATE_KEY }}
          GOOGLE_SERVICE_ACCOUNT_CLIENT_EMAIL=${{ secrets.GOOGLE_SERVICE_ACCOUNT_CLIENT_EMAIL }}
          GOOGLE_SERVICE_ACCOUNT_CLIENT_ID=${{ secrets.GOOGLE_SERVICE_ACCOUNT_CLIENT_ID }}
          GOOGLE_SERVICE_ACCOUNT_AUTH_URI=${{ secrets.GOOGLE_SERVICE_ACCOUNT_AUTH_URI }}
          GOOGLE_SERVICE_ACCOUNT_TOKEN_URI=${{ secrets.GOOGLE_SERVICE_ACCOUNT_TOKEN_URI }}
          GOOGLE_SERVICE_ACCOUNT_AUTH_PROVIDER_X509_CERT_URL=${{ secrets.GOOGLE_SERVICE_ACCOUNT_AUTH_PROVIDER_X509_CERT_URL }}
          GOOGLE_SERVICE_ACCOUNT_CLIENT_X509_CERT_URL=${{ secrets.GOOGLE_SERVICE_ACCOUNT_CLIENT_X509_CERT_URL }}
          GOOGLE_SERVICE_ACCOUNT_UNIVERSE_DOMAIN=${{ secrets.GOOGLE_SERVICE_ACCOUNT_UNIVERSE_DOMAIN }}
```

## GitLab CI Example

Here's an example GitLab CI configuration:

```yaml
stages:
  - build
  - deploy

variables:
  DOCKER_DRIVER: overlay2
  DOCKER_TLS_CERTDIR: ""

build:
  stage: build
  image: docker:20.10.16
  services:
    - docker:20.10.16-dind
  before_script:
    - docker login -u $CI_REGISTRY_USER -p $CI_REGISTRY_PASSWORD $CI_REGISTRY
    # Option 1: Using environment variables for Google Cloud authentication (recommended)
    # The service account credentials are passed as environment variables in GitLab CI/CD settings
  script:
    - docker-compose build
    - docker-compose push
  only:
    - main

deploy:
  stage: deploy
  image: alpine:latest
  before_script:
    - apk add --no-cache openssh-client
    - mkdir -p ~/.ssh
    - echo "$SSH_PRIVATE_KEY" > ~/.ssh/id_rsa
    - chmod 600 ~/.ssh/id_rsa
    - ssh-keyscan -H $DEPLOYMENT_SERVER_IP >> ~/.ssh/known_hosts
  script:
    - scp docker-compose.yml $DEPLOYMENT_USER@$DEPLOYMENT_SERVER_IP:/path/to/deployment/
    - ssh $DEPLOYMENT_USER@$DEPLOYMENT_SERVER_IP "cd /path/to/deployment/ && docker-compose pull && docker-compose up -d"
  only:
    - main
```

## Local Development

For local development, copy `.env.example` to `.env` and fill in the required values:

```bash
cp .env.example .env
# Edit .env with your values
```

For Google Cloud authentication, you have two options:

### Option 1: Using Environment Variables (Recommended)

Add your service account credentials directly to the `.env` file:

```bash
# Add these lines to your .env file
GOOGLE_SERVICE_ACCOUNT_TYPE=service_account
GOOGLE_SERVICE_ACCOUNT_PROJECT_ID=your-project-id
GOOGLE_SERVICE_ACCOUNT_PRIVATE_KEY_ID=your-private-key-id
GOOGLE_SERVICE_ACCOUNT_PRIVATE_KEY="-----BEGIN PRIVATE KEY-----\nYOUR_PRIVATE_KEY_HERE\n-----END PRIVATE KEY-----\n"
GOOGLE_SERVICE_ACCOUNT_CLIENT_EMAIL=your-service-account@your-project-id.iam.gserviceaccount.com
# ... and so on with the rest of the service account credentials
```

### Option 2: Using a Service Account Key File (Legacy)

Place your service account key in the `secrets` directory:

```bash
mkdir -p secrets
cp path/to/your/service-account-key.json secrets/service-account-key.json
```

Start the application with:

```bash
docker-compose up -d
```
