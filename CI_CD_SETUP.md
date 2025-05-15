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

## Service Account Key

The Google Cloud service account key is required for the Gemini Python service. This should be stored as a secret in your CI/CD platform and injected during the build process.

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
    
    - name: Create secrets directory
      run: mkdir -p secrets
    
    - name: Create service account key file
      run: echo '${{ secrets.GCP_SERVICE_ACCOUNT_KEY }}' > secrets/service-account-key.json
    
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
          GEMINI_API_KEY=${{ secrets.GEMINI_API_KEY }}
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
    - mkdir -p secrets
    - echo "$GCP_SERVICE_ACCOUNT_KEY" > secrets/service-account-key.json
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

Then place your service account key in the `secrets` directory:

```bash
cp path/to/your/service-account-key.json secrets/service-account-key.json
```

Start the application with:

```bash
docker-compose up -d
```
