# Google Cloud Service Account Setup

## Service Account Authentication Options

There are two ways to authenticate with Google Cloud for using the Gemini API:

### Option 1: Using Environment Variables (Recommended)

The service can now use service account credentials directly from environment variables. This is the recommended approach, especially for containerized deployments and CI/CD pipelines.

1. Go to the [Google Cloud Console](https://console.cloud.google.com/)
2. Navigate to "IAM & Admin" > "Service Accounts"
3. Create a new service account or use an existing one
4. Ensure the service account has the following roles:
   - `Vertex AI User`
   - `Vertex AI Service Agent`
5. Create a key for the service account (JSON format)
6. Download the key file
7. Add the contents of the key file to your main `.env` file in the root directory using the following format:

```
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

Alternatively, you can still use a service account key file:

1. Follow steps 1-6 from Option 1
2. Rename the key file to `service-account-key.json`
3. Place it in the root directory of the gemini-python-service (same level as this README file)
4. Set the `GOOGLE_APPLICATION_CREDENTIALS` environment variable in the `.env` file to point to this file:

```
GOOGLE_APPLICATION_CREDENTIALS="./service-account-key.json"
```

## Gemini 2.0 Models Configuration

The service is configured to use the following Gemini 2.0 models in order of preference:

1. `gemini-2.0-flash-001` (Primary model - configured in .env)
2. `gemini-2.0-flash-lite-001` (Fallback model)

The service will try each model in sequence until one works. This provides resilience in case one of the models is not available in your region or project.

### Model Capabilities

- `gemini-2.0-flash-001`: Gemini 2.0 Flash model - can process both text and images with high performance
- `gemini-2.0-flash-lite-001`: Gemini 2.0 Flash Lite model - similar to Flash but optimized for faster response times

### Model Parameters

The service configures the following parameters for optimal performance with Gemini 2.0 models:

- `temperature`: 0.1-0.2 (lower for verification, slightly higher for extraction)
- `max_output_tokens`: 1024-4096 (higher for verification to accommodate detailed responses)
- `top_p`: 0.95
- `top_k`: 40

## Alternative: Using Application Default Credentials (ADC)

If you prefer to use Application Default Credentials:

1. Install the [Google Cloud SDK](https://cloud.google.com/sdk/docs/install)
2. Run `gcloud auth application-default login`
3. Comment out the `GOOGLE_APPLICATION_CREDENTIALS` line in the `.env` file

## Security Note

Never commit service account keys to version control. The `.gitignore` file should already be configured to ignore `*.json` files in this directory, but double-check to be sure.
