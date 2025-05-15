# Google Cloud Service Account Setup

## Important: Service Account Key Required

To use the Gemini API, you need to place a valid Google Cloud service account key file in this directory.

## Steps to Create a Service Account Key:

1. Go to the [Google Cloud Console](https://console.cloud.google.com/)
2. Navigate to "IAM & Admin" > "Service Accounts"
3. Create a new service account or use an existing one
4. Ensure the service account has the following roles:
   - `Vertex AI User`
   - `Vertex AI Service Agent`
5. Create a key for the service account (JSON format)
6. Download the key file
7. Rename it to `service-account-key.json`
8. Place it in the root directory of the gemini-python-service (same level as this README file)

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
