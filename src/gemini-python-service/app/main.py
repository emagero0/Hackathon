from fastapi import FastAPI, HTTPException, Depends
from contextlib import asynccontextmanager
import logging

from . import schemas, services, config

# Configure logging
logger = logging.getLogger(__name__)
logging.basicConfig(level=logging.INFO) # Adjust log level as needed


@asynccontextmanager
async def lifespan(app: FastAPI):
    # Code to run on startup
    logger.info("Application startup...")
    services.init_vertexai() # Initialize Vertex AI client
    yield
    # Code to run on shutdown (if any)
    logger.info("Application shutdown...")

app = FastAPI(
    title="AI Document Verification Microservice (Gemini)",
    description="A microservice to interact with Google Gemini for document extraction and verification.",
    version="0.1.0",
    lifespan=lifespan
)

# --- API Endpoints ---

@app.post("/extract_identifiers", response_model=schemas.IdentifierExtractionResponse)
async def extract_identifiers(request: schemas.IdentifierExtractionRequest):
    """
    Extracts key identifiers from a document using Gemini.
    """
    logger.info(f"Received request for /extract_identifiers for job_no: {request.job_no}, doc_type: {request.document_type}")
    try:
        response_data = await services.extract_identifiers_from_gemini(request)
        if response_data.error_message:
            # Consider if this should be a 4xx or 5xx error based on the nature of error_message
            logger.error(f"Error in extract_identifiers_from_gemini: {response_data.error_message}")
            # For now, let's return 500 if an error message is present in the service response
            raise HTTPException(status_code=500, detail=response_data.error_message)
        return response_data
    except Exception as e:
        logger.error(f"Unhandled exception in /extract_identifiers endpoint: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail=f"An unexpected error occurred: {str(e)}")

@app.post("/verify_document", response_model=schemas.VerificationResponse)
async def verify_document(request: schemas.VerificationRequest):
    """
    Verifies a document against ERP data using Gemini.
    """
    logger.info(f"Received request for /verify_document for job_no: {request.job_no}, doc_type: {request.document_type}")
    try:
        response_data = await services.verify_document_with_gemini(request)
        if response_data.error_message and not (response_data.discrepancies or response_data.field_confidences):
             # If there's an error message and no actual verification results, treat as an error
            logger.error(f"Error in verify_document_with_gemini: {response_data.error_message}")
            raise HTTPException(status_code=500, detail=response_data.error_message)
        return response_data
    except Exception as e:
        logger.error(f"Unhandled exception in /verify_document endpoint: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail=f"An unexpected error occurred: {str(e)}")

@app.post("/classify_document", response_model=schemas.ClassificationResponse)
async def classify_document(request: schemas.ClassificationRequest):
    """
    Classifies a document as Sales Quote, ProformaInvoice, or Job Consumption.
    """
    logger.info(f"Received request for /classify_document for job_no: {request.job_no}")
    try:
        response_data = await services.classify_document_with_gemini(request)
        if response_data.error_message:
            logger.error(f"Error in classify_document_with_gemini: {response_data.error_message}")
            raise HTTPException(status_code=500, detail=response_data.error_message)
        return response_data
    except Exception as e:
        logger.error(f"Unhandled exception in /classify_document endpoint: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail=f"An unexpected error occurred: {str(e)}")

@app.get("/health", summary="Health Check")
def health_check():
    """
    Health check endpoint with detailed status information.
    """
    logger.info("Health check endpoint called.")

    # Determine status based on Vertex AI initialization
    status = "ok" if services._vertex_ai_initialized else "degraded"
    message = "Gemini Document Verification Service is running."

    if not services._vertex_ai_initialized:
        message += " WARNING: Vertex AI is not initialized. Document verification will fail."

    # Check credentials status
    credentials_status = "not_configured"
    credentials_message = "No service account credentials configured."
    credentials_type = "none"

    # Check if service account credentials are provided via environment variables
    if config.settings.has_service_account_env_vars():
        credentials_status = "available"
        credentials_message = f"Service account credentials found in environment variables for: {config.settings.google_service_account_client_email}"
        credentials_type = "service_account_env"
    # Check if service account credentials file exists (legacy method)
    elif config.settings.google_application_credentials:
        import os
        if os.path.exists(config.settings.google_application_credentials):
            credentials_status = "available"
            credentials_message = f"Service account credentials found at: {config.settings.google_application_credentials}"
            credentials_type = "service_account_file"
        else:
            credentials_status = "missing"
            credentials_message = f"Service account credentials file not found at: {config.settings.google_application_credentials}"
            credentials_type = "service_account_file_missing"
    else:
        # Assuming Application Default Credentials
        credentials_type = "adc"
        credentials_message = "Using Application Default Credentials (ADC)"

    return {
        "status": status,
        "message": message,
        "gcp_project": config.settings.gcp_project_id,
        "gcp_location": config.settings.gcp_location,
        "gemini_model": config.settings.gemini_model_name,
        "vertex_ai_initialized": services._vertex_ai_initialized,
        "credentials": {
            "status": credentials_status,
            "message": credentials_message,
            "type": credentials_type
        }
    }

# To run this app (from the root of gemini-python-service directory):
# uvicorn app.main:app --reload --port 8000
