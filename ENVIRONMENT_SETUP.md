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

For the Gemini Python service, you need to:

1. Create a service account in Google Cloud Console
2. Generate a JSON key for the service account
3. Save the key as `service-account-key.json` in the `src/gemini-python-service/` directory

See `src/gemini-python-service/README_SERVICE_ACCOUNT.md` for more details.
