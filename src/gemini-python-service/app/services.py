import base64
import json
from typing import List, Dict, Any
import logging

import vertexai
from vertexai.generative_models import GenerativeModel, Part, Image, GenerationConfig # Added GenerationConfig
from google.auth.exceptions import DefaultCredentialsError


from .config import settings
from . import schemas

# Configure logging
logger = logging.getLogger(__name__)
logging.basicConfig(level=logging.INFO) # Adjust log level as needed

# --- Vertex AI Initialization ---
_vertex_ai_initialized = False

def init_vertexai():
    """
    Initializes the Vertex AI client.
    Should be called once on application startup.
    """
    global _vertex_ai_initialized
    if _vertex_ai_initialized:
        logger.info("Vertex AI already initialized.")
        return

    try:
        logger.info(f"Initializing Vertex AI with Project ID: {settings.gcp_project_id}, Location: {settings.gcp_location}")
        from google.oauth2 import service_account
        import json
        import tempfile

        # Check if service account credentials are provided via environment variables
        if settings.has_service_account_env_vars():
            try:
                # Create a service account info dictionary from environment variables
                service_account_info = {
                    "type": settings.google_service_account_type,
                    "project_id": settings.google_service_account_project_id,
                    "private_key_id": settings.google_service_account_private_key_id,
                    "private_key": settings.google_service_account_private_key,
                    "client_email": settings.google_service_account_client_email,
                    "client_id": settings.google_service_account_client_id,
                    "auth_uri": settings.google_service_account_auth_uri,
                    "token_uri": settings.google_service_account_token_uri,
                    "auth_provider_x509_cert_url": settings.google_service_account_auth_provider_x509_cert_url,
                    "client_x509_cert_url": settings.google_service_account_client_x509_cert_url,
                    "universe_domain": settings.google_service_account_universe_domain
                }

                # Create credentials from the service account info
                credentials = service_account.Credentials.from_service_account_info(service_account_info)
                vertexai.init(project=settings.gcp_project_id, location=settings.gcp_location, credentials=credentials)
                logger.info("Vertex AI initialized using service account credentials from environment variables.")
                _vertex_ai_initialized = True
            except Exception as e:
                logger.error(f"Failed to initialize Vertex AI with service account credentials from environment variables: {e}", exc_info=True)
                _vertex_ai_initialized = False

        # Check if credentials file exists when specified (legacy method)
        elif settings.google_application_credentials:
            import os
            if not os.path.exists(settings.google_application_credentials):
                logger.error(f"Service account credentials file not found at: {settings.google_application_credentials}")
                logger.error("Please place a valid service account key file at this location or update the GOOGLE_APPLICATION_CREDENTIALS path in .env")
                _vertex_ai_initialized = False
                return

            try:
                credentials = service_account.Credentials.from_service_account_file(settings.google_application_credentials)
                vertexai.init(project=settings.gcp_project_id, location=settings.gcp_location, credentials=credentials)
                logger.info("Vertex AI initialized using service account credentials from file.")
                _vertex_ai_initialized = True
            except Exception as e:
                logger.error(f"Failed to initialize Vertex AI with service account credentials from file: {e}", exc_info=True)
                _vertex_ai_initialized = False
        else:
            # Relies on Application Default Credentials (ADC)
            try:
                vertexai.init(project=settings.gcp_project_id, location=settings.gcp_location)
                logger.info("Vertex AI initialized using Application Default Credentials (ADC).")
                _vertex_ai_initialized = True
            except DefaultCredentialsError:
                logger.error(
                    "Google Cloud Default Credentials not found. "
                    "Ensure you've run 'gcloud auth application-default login' "
                    "or set GOOGLE_APPLICATION_CREDENTIALS environment variable in .env file."
                )
                logger.error("See README_SERVICE_ACCOUNT.md for detailed instructions on setting up authentication.")
                _vertex_ai_initialized = False
            except Exception as e:
                logger.error(f"Failed to initialize Vertex AI with ADC: {e}", exc_info=True)
                _vertex_ai_initialized = False
    except Exception as e:
        logger.error(f"Failed to initialize Vertex AI: {e}", exc_info=True)
        _vertex_ai_initialized = False


# --- Helper Functions ---
def _build_gemini_parts_from_request(
    prompt_text: str,
    document_images: List[schemas.DocumentImage]
) -> List[Part]:
    """Helper to build Parts for Gemini request from base64 images."""
    parts = [Part.from_text(prompt_text)]
    for doc_image in document_images:
        try:
            image_bytes = base64.b64decode(doc_image.image_base64)
            image_part = Part.from_data(data=image_bytes, mime_type=doc_image.mime_type)
            parts.append(image_part)
        except Exception as e:
            logger.error(f"Failed to decode base64 image or create Part: {e}", exc_info=True)
            # Optionally, skip this image or raise an error
    return parts

# --- Service Functions ---
async def extract_identifiers_from_gemini(
    request_data: schemas.IdentifierExtractionRequest
) -> schemas.IdentifierExtractionResponse:
    """
    Extracts identifiers from documents using Gemini.
    """
    if not _vertex_ai_initialized:
        logger.error("Vertex AI not initialized. Cannot process request.")
        return schemas.IdentifierExtractionResponse(extracted_identifiers={}, error_message="Vertex AI client not initialized.")

    # List of model names to try in order of preference
    model_names = [
        settings.gemini_model_name,      # Try the configured model first (gemini-2.0-flash-001)
        "gemini-2.0-flash-lite-001",     # Gemini 2.0 Flash Lite model as fallback
    ]

    # Fields to extract based on document type
    fields_to_extract_map = {
        "salesquote": ["salesQuoteNo", "customerName"],
        "proformainvoice": ["proformaInvoiceNo"],
        "jobconsumption": ["jobConsumptionNo"] # Example
    }
    expected_fields = fields_to_extract_map.get(request_data.document_type.lower(), ["documentId"])

    # Prepare the prompt
    prompt_structure = {
        "task_description": "Extract key identifiers from the provided document.",
        "document_type_context": request_data.document_type,
        "job_number_context": request_data.job_no,
        "instructions": f"Analyze the document image(s) and extract the following fields: {', '.join(expected_fields)}. "
                        "Return the extracted information as a flat JSON object where keys are the field names "
                        "and values are the extracted strings. If a field is not found, omit it from the JSON or return null for its value.",
        "output_format_example": {field: "extracted_value" for field in expected_fields}
    }
    prompt_text = json.dumps(prompt_structure, indent=2)
    logger.debug(f"Gemini Identifier Extraction Prompt for job {request_data.job_no}:\n{prompt_text}")

    # Prepare the image parts
    gemini_parts = _build_gemini_parts_from_request(prompt_text, request_data.document_images)
    if not any(isinstance(part, Image) for part in gemini_parts if hasattr(part, '_image')): # Check if any image parts were successfully created
         if any(isinstance(part, Part) and part.inline_data for part in gemini_parts): # Check based on inline_data for older SDK versions
            pass # At least one image part exists
         elif len(request_data.document_images) > 0 : # If images were provided but not processed
            logger.warning("No image parts were successfully created for Gemini request, though images were provided.")
            # Decide if to proceed without images or return error

    # Configure generation parameters
    generation_config = GenerationConfig(temperature=0.2, max_output_tokens=1024)

    # Try each model in sequence until one works
    last_error = None
    for model_name in model_names:
        try:
            logger.info(f"Attempting to use model: {model_name}")
            model = GenerativeModel(model_name)

            # All models in our list are vision-capable
            # Configure generation parameters for Gemini 2.0 models
            if "gemini-2.0" in model_name:
                # Gemini 2.0 models may need specific configuration
                generation_config = GenerationConfig(
                    temperature=0.2,
                    max_output_tokens=1024,
                    top_p=0.95,
                    top_k=40
                )

            # Use the full multimodal request for all models
            response = await model.generate_content_async(gemini_parts, generation_config=generation_config)

            raw_response = response.text
            logger.debug(f"Raw model response for identifier extraction (job {request_data.job_no}): {raw_response}")

            # Try to extract JSON from the response, handling potential text wrapping
            try:
                # First try direct JSON parsing
                extracted_data = json.loads(raw_response)
            except json.JSONDecodeError:
                # If that fails, try to extract JSON from the text
                import re
                json_match = re.search(r'({[\s\S]*})', raw_response)
                if json_match:
                    try:
                        extracted_data = json.loads(json_match.group(1))
                    except json.JSONDecodeError:
                        # If still failing, try a more lenient approach
                        logger.warning(f"Could not parse JSON directly. Attempting to extract field values manually.")
                        extracted_data = {}
                        for field in expected_fields:
                            # Look for patterns like "field": "value" or field: value
                            pattern = rf'["\']?{field}["\']?\s*:\s*["\']?(.*?)["\']?[,}}]'
                            match = re.search(pattern, raw_response)
                            if match:
                                extracted_data[field] = match.group(1).strip()
                else:
                    raise ValueError(f"Could not extract JSON from response: {raw_response}")

            # If we get here, the model worked
            logger.info(f"Successfully used model: {model_name}")
            return schemas.IdentifierExtractionResponse(extracted_identifiers=extracted_data)

        except Exception as e:
            last_error = e
            logger.warning(f"Failed to use model {model_name}: {e}")
            continue  # Try the next model

    # If we get here, all models failed
    error_message = f"All Gemini models failed. Last error: {str(last_error)}"
    logger.error(error_message)
    return schemas.IdentifierExtractionResponse(extracted_identifiers={}, error_message=error_message)


# Helper functions for building document-specific prompts
def _build_sales_quote_prompt(job_no: str, erp_data: Dict[str, Any]) -> Dict[str, Any]:
    """
    Build a structured prompt for Sales Quote verification.
    """
    # Extract the necessary data from erp_data
    sales_quote_header = erp_data.get("salesQuoteHeader", {})
    sales_quote_lines = erp_data.get("salesQuoteLines", [])

    # Build the prompt structure according to the template
    prompt = {
        "requestContext": {
            "jobId": job_no,
            "documentType": "SalesQuote",
            "documentFileName": "Sales Quote (visual data provided)"
        },
        "expectedErpData": {
            "salesQuoteHeader": sales_quote_header,
            "salesQuoteLines": sales_quote_lines
        },
        "verificationInstructions": {
            "outputSchema": {
                "discrepancies": [
                    {
                        "discrepancy_type": "VALUE_MISMATCH | MISSING_IN_DOCUMENT | UNEXPECTED_IN_DOCUMENT | FORMAT_ERROR",
                        "field_name": "string (e.g., 'header.Sell_to_Customer_Name', 'lines.0.Quantity')",
                        "expected_value": "any",
                        "actual_value": "any",
                        "description": "string (Formatted like: 'Sales Quote Header vs BC Mismatch: Document Sell_to_Customer_Name ('Actual Name Ltd') != BC Sell_to_Customer_Name ('Customer A Inc.').')",
                        "confidence": "float (0.0-1.0)"
                    }
                ],
                "field_confidences": [
                    {
                        "field_name": "string",
                        "extracted_value": "any",
                        "verification_confidence": "float (0.0-1.0)",
                        "extraction_confidence": "float (0.0-1.0)"
                    }
                ],
                "overall_verification_confidence": "float (0.0-1.0)"
            },
            "headerFieldsToVerify": [
                {"documentFieldName": "Sales Quote Number", "erpPath": "salesQuoteHeader.No", "comparisonType": "exact_match_alphanumeric_only", "discrepancyFormat": "Sales Quote Header vs BC Mismatch: Document Sales Quote Number ('{actual}') != BC No ('{expected}')."},
                {"documentFieldName": "Customer Account Number", "erpPath": "salesQuoteHeader.Sell_to_Customer_No", "comparisonType": "exact_match_alphanumeric_only", "discrepancyFormat": "Sales Quote Header vs BC Mismatch: Document Account No ('{actual}') != BC Sell_to_Customer_No ('{expected}')."},
                {"documentFieldName": "Customer Name", "erpPath": "salesQuoteHeader.Sell_to_Customer_Name", "comparisonType": "fuzzy_match_ignore_case_whitespace", "discrepancyFormat": "Sales Quote Header vs BC Mismatch: Document Customer Name ('{actual}') != BC Sell_to_Customer_Name ('{expected}')."},
                {"documentFieldName": "Total Amount Including VAT", "erpPath": "salesQuoteHeader.Amount_Including_VAT", "comparisonType": "numeric_match_2_decimals", "discrepancyFormat": "Sales Quote Header vs BC Mismatch: Document Total Amount ('{actual}') != BC Amount_Including_VAT ('{expected}')."}
            ],
            "lineItemFieldsToVerify": [
                {"documentFieldName": "Description", "erpPath": "Description", "comparisonType": "fuzzy_match_ignore_case_whitespace", "isKeyForMatching": True},
                {"documentFieldName": "Quantity", "erpPath": "Quantity", "comparisonType": "numeric_match_allow_integer_vs_decimal", "discrepancyFormat": "Sales Quote PDF vs BC Line Item '{matchedDescription}' Mismatch: Quantities differ (Document: {actual}, BC: {expected})."}
            ],
            "generalInstructions": [
                "Analyze the provided document images which represent a Sales Quote.",
                "Extract all header fields as specified in 'headerFieldsToVerify'.",
                "Extract all line items. For each line item, extract fields specified in 'lineItemFieldsToVerify'.",
                "Match extracted line items to ERP line items primarily using 'Description' (fuzzy match).",
                "For each specified field, compare the extracted value from the document with the value from 'expectedErpData' using the specified 'comparisonType'.",
                "If a discrepancy is found, use the 'discrepancyFormat' string to generate the description. Replace {actual} with the value found in the document and {expected} with the value from 'expectedErpData'. For line items, {matchedDescription} is the description of the matched line item.",
                "Report all findings in the JSON format defined in 'outputSchema'.",
                "Provide confidence scores (0.0 to 1.0) for each extracted field's accuracy and for each verification decision (match/mismatch)."
            ]
        }
    }

    return prompt

def _build_proforma_invoice_prompt(job_no: str, erp_data: Dict[str, Any]) -> Dict[str, Any]:
    """
    Build a structured prompt for Proforma Invoice verification.
    """
    # Extract the necessary data from erp_data
    sales_invoice_header = erp_data.get("salesInvoiceHeader", {})
    sales_invoice_lines = erp_data.get("salesInvoiceLines", [])

    # Build the prompt structure according to the template
    prompt = {
        "requestContext": {
            "jobId": job_no,
            "documentType": "ProformaInvoice",
            "documentFileName": "Proforma Invoice (visual data provided)"
        },
        "expectedErpData": {
            "salesInvoiceHeader": sales_invoice_header,
            "salesInvoiceLines": sales_invoice_lines
        },
        "verificationInstructions": {
            "outputSchema": {
                "discrepancies": [
                    {
                        "discrepancy_type": "VALUE_MISMATCH | MISSING_IN_DOCUMENT | UNEXPECTED_IN_DOCUMENT | FORMAT_ERROR",
                        "field_name": "string (e.g., 'header.Sell_to_Customer_Name', 'lines.0.Quantity')",
                        "expected_value": "any",
                        "actual_value": "any",
                        "description": "string (Formatted like: 'Proforma Invoice Header vs BC Mismatch: Document Sell_to_Customer_Name ('Actual Name Ltd') != BC Sell_to_Customer_Name ('Customer A Inc.').')",
                        "confidence": "float (0.0-1.0)"
                    }
                ],
                "field_confidences": [
                    {
                        "field_name": "string",
                        "extracted_value": "any",
                        "verification_confidence": "float (0.0-1.0)",
                        "extraction_confidence": "float (0.0-1.0)"
                    }
                ],
                "overall_verification_confidence": "float (0.0-1.0)"
            },
            "headerFieldsToVerify": [
                {"documentFieldName": "Proforma Invoice Number", "erpPath": "salesInvoiceHeader.No", "comparisonType": "exact_match_alphanumeric_only", "discrepancyFormat": "Proforma Invoice Header vs BC Mismatch: Document Proforma Invoice Number ('{actual}') != BC No ('{expected}')."},
                {"documentFieldName": "Customer Account Number", "erpPath": "salesInvoiceHeader.Sell_to_Customer_No", "comparisonType": "exact_match_alphanumeric_only", "discrepancyFormat": "Proforma Invoice Header vs BC Mismatch: Document Account No ('{actual}') != BC Sell_to_Customer_No ('{expected}')."},
                {"documentFieldName": "Customer Name", "erpPath": "salesInvoiceHeader.Sell_to_Customer_Name", "comparisonType": "fuzzy_match_ignore_case_whitespace", "discrepancyFormat": "Proforma Invoice Header vs BC Mismatch: Document Customer Name ('{actual}') != BC Sell_to_Customer_Name ('{expected}')."}
            ],
            "lineItemFieldsToVerify": [
                {"documentFieldName": "Description", "erpPath": "Description", "comparisonType": "fuzzy_match_ignore_case_whitespace", "isKeyForMatching": True},
                {"documentFieldName": "Quantity", "erpPath": "Quantity", "comparisonType": "numeric_match_allow_integer_vs_decimal", "discrepancyFormat": "Proforma Invoice PDF vs BC Line Item '{matchedDescription}' Mismatch: Quantities differ (Document: {actual}, BC: {expected})."}
            ],
            "generalInstructions": [
                "Analyze the provided document images which represent a Proforma Invoice.",
                "Extract all header fields as specified in 'headerFieldsToVerify'.",
                "Extract all line items. For each line item, extract fields specified in 'lineItemFieldsToVerify'.",
                "Match extracted line items to ERP line items primarily using 'Description' (fuzzy match).",
                "For each specified field, compare the extracted value from the document with the value from 'expectedErpData' using the specified 'comparisonType'.",
                "If a discrepancy is found, use the 'discrepancyFormat' string to generate the description. Replace {actual} with the value found in the document and {expected} with the value from 'expectedErpData'. For line items, {matchedDescription} is the description of the matched line item.",
                "Report all findings in the JSON format defined in 'outputSchema'.",
                "Provide confidence scores (0.0 to 1.0) for each extracted field's accuracy and for each verification decision (match/mismatch)."
            ]
        }
    }

    return prompt

def _build_job_consumption_prompt(job_no: str, erp_data: Dict[str, Any]) -> Dict[str, Any]:
    """
    Build a structured prompt for Job Consumption verification.
    """
    # Extract the necessary data from erp_data
    job_ledger_entries = erp_data.get("jobLedgerEntries", [])

    # Build the prompt structure according to the template
    prompt = {
        "requestContext": {
            "jobId": job_no,
            "documentType": "JobConsumption",
            "documentFileName": "Job Consumption (visual data provided)"
        },
        "expectedErpData": {
            "jobLedgerEntries": job_ledger_entries
        },
        "verificationInstructions": {
            "outputSchema": {
                "discrepancies": [
                    {
                        "discrepancy_type": "VALUE_MISMATCH | MISSING_IN_DOCUMENT | UNEXPECTED_IN_DOCUMENT | FORMAT_ERROR",
                        "field_name": "string (e.g., 'header.Job_No', 'lines.0.Quantity')",
                        "expected_value": "any",
                        "actual_value": "any",
                        "description": "string (Formatted like: 'Job Consumption Header vs BC Mismatch: Document Job No ('{actual}') != BC Job_No ('{expected}').')",
                        "confidence": "float (0.0-1.0)"
                    }
                ],
                "field_confidences": [
                    {
                        "field_name": "string",
                        "extracted_value": "any",
                        "verification_confidence": "float (0.0-1.0)",
                        "extraction_confidence": "float (0.0-1.0)"
                    }
                ],
                "overall_verification_confidence": "float (0.0-1.0)"
            },
            "headerFieldsToVerify": [
                {"documentFieldName": "Job Number", "erpPath": "requestContext.jobId", "comparisonType": "exact_match_alphanumeric_only", "discrepancyFormat": "Job Consumption Header vs BC Mismatch: Document Job Number ('{actual}') != Expected Job No ('{expected}')."}
            ],
            "lineItemFieldsToVerify": [
                {"documentFieldName": "Description", "erpPath": "Description", "comparisonType": "fuzzy_match_ignore_case_whitespace", "isKeyForMatching": True},
                {"documentFieldName": "Quantity", "erpPath": "Quantity", "comparisonType": "numeric_match_allow_integer_vs_decimal", "discrepancyFormat": "Job Consumption PDF vs BC Line Item '{matchedDescription}' Mismatch: Quantities differ (Document: {actual}, BC: {expected})."},
                {"documentFieldName": "Item/Resource No", "erpPath": "No", "comparisonType": "exact_match_alphanumeric_only", "discrepancyFormat": "Job Consumption PDF vs BC Line Item '{matchedDescription}' Mismatch: Item/Resource No differs (Document: {actual}, BC: {expected}).", "optionalInDocument": True},
                {"documentFieldName": "Type", "erpPath": "Type", "comparisonType": "exact_match_ignore_case", "discrepancyFormat": "Job Consumption PDF vs BC Line Item '{matchedDescription}' Mismatch: Type differs (Document: {actual}, BC: {expected}).", "optionalInDocument": True}
            ],
            "additionalVerifications": [
                {"verificationType": "signature_presence", "description": "Check if the 'Received By' section contains a signature or name.", "discrepancyFormat": "Job Consumption PDF Missing Signature: The 'Received By' section does not appear to be signed or contain a name."}
            ],
            "generalInstructions": [
                "Analyze the provided document images which represent a Job Consumption document.",
                "Extract the Job Number from the header and verify it matches the jobId in the requestContext.",
                "Extract all line items. For each line item, extract fields specified in 'lineItemFieldsToVerify'.",
                "Match extracted line items to ERP job ledger entries primarily using 'Description' (fuzzy match).",
                "For each specified field, compare the extracted value from the document with the value from 'expectedErpData' using the specified 'comparisonType'.",
                "If a discrepancy is found, use the 'discrepancyFormat' string to generate the description. Replace {actual} with the value found in the document and {expected} with the value from 'expectedErpData'. For line items, {matchedDescription} is the description of the matched line item.",
                "Check if the 'Received By' section contains a signature or name. If not, report it as a discrepancy.",
                "Report all findings in the JSON format defined in 'outputSchema'.",
                "Provide confidence scores (0.0 to 1.0) for each extracted field's accuracy and for each verification decision (match/mismatch)."
            ]
        }
    }

    return prompt

def _build_document_classification_prompt() -> Dict[str, Any]:
    """
    Build a structured prompt for document classification.
    """
    prompt = {
        "task": "document_classification",
        "possibleDocumentTypes": [
            {
                "type": "SalesQuote",
                "characteristics": [
                    "Contains 'SALES QUOTE' in the header",
                    "Has quote number, usually labeled as 'SQ' followed by numbers",
                    "Contains customer information",
                    "Has line items with descriptions, quantities, and prices",
                    "Includes payment options like 'Click here to pay via Mpesa, Click here to pay via VISA/Master Card'"
                ]
            },
            {
                "type": "ProformaInvoice",
                "characteristics": [
                    "Contains 'PRO FORMA INVOICE' in the header",
                    "Contains phrase 'This is not a Tax Invoice. A Tax Invoice will be issued upon supply...'",
                    "Has invoice number, usually labeled as 'Tax Invoice No'",
                    "Contains customer information",
                    "Has line items with descriptions, quantities, and prices",
                    "Clearly indicates pre-sale, quotation-like purpose"
                ]
            },
            {
                "type": "JobConsumption",
                "characteristics": [
                    "Contains 'JOB SHIPMENT' in the header",
                    "Has job shipment number field labeled as 'Job Shipment No'",
                    "Contains 'INSTRUCTED BY', 'DISPATCHED BY', 'RECEIVED BY' sections",
                    "Has logistics-related, dispatch-focused format"
                ]
            }
        ],
        "instructions": [
            "Analyze the provided document images.",
            "Determine which document type it matches based on the characteristics listed.",
            "Return the document type as one of: 'SalesQuote', 'ProformaInvoice', or 'JobConsumption'.",
            "If you cannot confidently classify the document, return 'UNKNOWN'."
        ],
        "outputFormat": {
            "documentType": "The classified document type (SalesQuote, ProformaInvoice, JobConsumption, or UNKNOWN)",
            "confidence": "A confidence score between 0.0 and 1.0",
            "reasoning": "Brief explanation of why this classification was chosen"
        }
    }
    return prompt

async def classify_document_with_gemini(
    request_data: schemas.ClassificationRequest
) -> schemas.ClassificationResponse:
    """
    Classifies a document using Gemini.
    """
    if not _vertex_ai_initialized:
        logger.error("Vertex AI not initialized. Cannot process request.")
        return schemas.ClassificationResponse(
            document_type="UNKNOWN",
            confidence=0.0,
            error_message="Vertex AI client not initialized."
        )

    # List of model names to try in order of preference
    model_names = [
        settings.gemini_model_name,      # Try the configured model first (gemini-2.0-flash-001)
        "gemini-2.0-flash-lite-001",     # Gemini 2.0 Flash Lite model as fallback
    ]

    # Build the classification prompt
    prompt_structure = _build_document_classification_prompt()
    prompt_text = json.dumps(prompt_structure, indent=2)
    logger.debug(f"Gemini Classification Prompt for job {request_data.job_no}:\n{prompt_text}")

    # Build the parts for the Gemini request
    gemini_parts = _build_gemini_parts_from_request(prompt_text, request_data.document_images)

    # Configure generation parameters for better results
    generation_config = GenerationConfig(temperature=0.1, max_output_tokens=1024)

    # Try each model in sequence until one works
    last_error = None
    raw_response_text = None

    for model_name in model_names:
        try:
            logger.info(f"Attempting to use model: {model_name} for document classification")
            model = GenerativeModel(model_name)

            # All models in our list are vision-capable
            response = await model.generate_content_async(gemini_parts, generation_config=generation_config)
            raw_response_text = response.text
            logger.debug(f"Raw model response for document classification (job {request_data.job_no}): {raw_response_text}")

            # Try to extract JSON from the response
            try:
                # First try direct JSON parsing
                classification_data = json.loads(raw_response_text)
            except json.JSONDecodeError:
                # If that fails, try to extract JSON from the text
                import re
                json_match = re.search(r'({[\s\S]*})', raw_response_text)
                if json_match:
                    try:
                        classification_data = json.loads(json_match.group(1))
                    except json.JSONDecodeError:
                        # If still failing, try a more lenient approach
                        logger.warning(f"Could not parse JSON directly. Attempting to extract classification manually.")
                        classification_data = {}

                        # Look for document type
                        doc_type_match = re.search(r'documentType["\']?\s*:\s*["\']?(\w+)["\']?', raw_response_text)
                        if doc_type_match:
                            classification_data["documentType"] = doc_type_match.group(1)
                        else:
                            classification_data["documentType"] = "UNKNOWN"

                        # Look for confidence
                        confidence_match = re.search(r'confidence["\']?\s*:\s*([0-9.]+)', raw_response_text)
                        if confidence_match:
                            classification_data["confidence"] = float(confidence_match.group(1))
                        else:
                            classification_data["confidence"] = 0.0

                        # Look for reasoning
                        reasoning_match = re.search(r'reasoning["\']?\s*:\s*["\']?(.*?)["\']?[,}]', raw_response_text)
                        if reasoning_match:
                            classification_data["reasoning"] = reasoning_match.group(1)
                        else:
                            classification_data["reasoning"] = "No reasoning provided"
                else:
                    # If we can't extract JSON, look for keywords in the response
                    if "salesquote" in raw_response_text.lower() or "sales quote" in raw_response_text.lower():
                        classification_data = {"documentType": "SalesQuote", "confidence": 0.7, "reasoning": "Extracted from text response"}
                    elif "proformainvoice" in raw_response_text.lower() or "proforma invoice" in raw_response_text.lower():
                        classification_data = {"documentType": "ProformaInvoice", "confidence": 0.7, "reasoning": "Extracted from text response"}
                    elif "jobconsumption" in raw_response_text.lower() or "job consumption" in raw_response_text.lower() or "job shipment" in raw_response_text.lower():
                        classification_data = {"documentType": "JobConsumption", "confidence": 0.7, "reasoning": "Extracted from text response"}
                    else:
                        classification_data = {"documentType": "UNKNOWN", "confidence": 0.0, "reasoning": "Could not determine document type"}

            # Normalize the document type
            if "documentType" in classification_data:
                doc_type = classification_data["documentType"]
                if isinstance(doc_type, str):
                    doc_type = doc_type.strip()
                    # Normalize to expected values
                    if doc_type.lower() in ["salesquote", "sales quote", "sq"]:
                        classification_data["documentType"] = "SalesQuote"
                    elif doc_type.lower() in ["proformainvoice", "proforma invoice", "proforma", "pi"]:
                        classification_data["documentType"] = "ProformaInvoice"
                    elif doc_type.lower() in ["jobconsumption", "job consumption", "job shipment", "jobshipment", "jc"]:
                        classification_data["documentType"] = "JobConsumption"
                    else:
                        classification_data["documentType"] = "UNKNOWN"
            else:
                classification_data["documentType"] = "UNKNOWN"

            # Ensure confidence is a float
            if "confidence" in classification_data:
                try:
                    classification_data["confidence"] = float(classification_data["confidence"])
                except (ValueError, TypeError):
                    classification_data["confidence"] = 0.0
            else:
                classification_data["confidence"] = 0.0

            # Ensure reasoning is a string
            if "reasoning" not in classification_data or not classification_data["reasoning"]:
                classification_data["reasoning"] = "No reasoning provided"

            # If we get here, the model worked
            logger.info(f"Successfully used model: {model_name} for document classification")
            return schemas.ClassificationResponse(
                document_type=classification_data["documentType"],
                confidence=classification_data["confidence"],
                reasoning=classification_data["reasoning"]
            )

        except Exception as e:
            last_error = e
            logger.warning(f"Failed to use model {model_name} for document classification: {e}")
            continue  # Try the next model

    # If we get here, all models failed
    error_message = f"All Gemini models failed for document classification. Last error: {str(last_error)}"
    logger.error(error_message)
    return schemas.ClassificationResponse(
        document_type="UNKNOWN",
        confidence=0.0,
        error_message=error_message
    )

async def verify_document_with_gemini(
    request_data: schemas.VerificationRequest
) -> schemas.VerificationResponse:
    """
    Verifies document content against ERP data using Gemini.
    """
    if not _vertex_ai_initialized:
        logger.error("Vertex AI not initialized. Cannot process request.")
        return schemas.VerificationResponse(error_message="Vertex AI client not initialized.")

    # List of model names to try in order of preference
    model_names = [
        settings.gemini_model_name,      # Try the configured model first (gemini-2.0-flash-001)
        "gemini-2.0-flash-lite-001",     # Gemini 2.0 Flash Lite model as fallback
    ]

    # Select the appropriate prompt builder based on document type
    document_type = request_data.document_type.lower()

    if document_type == "salesquote":
        prompt_structure = _build_sales_quote_prompt(request_data.job_no, request_data.erp_data)
    elif document_type == "proformainvoice":
        prompt_structure = _build_proforma_invoice_prompt(request_data.job_no, request_data.erp_data)
    elif document_type == "jobconsumption":
        prompt_structure = _build_job_consumption_prompt(request_data.job_no, request_data.erp_data)
    else:
        logger.warning(f"Unknown document type: {document_type}. Using generic verification prompt.")
        # Fallback to a generic verification prompt
        output_schema_description = schemas.VerificationResponse.model_json_schema()
        prompt_structure = {
            "task_description": "Verify the provided document against the given ERP data. Identify discrepancies, assess confidence levels for extracted and verified fields, and determine an overall verification confidence.",
            "document_type_context": request_data.document_type,
            "job_number_context": request_data.job_no,
            "erp_data_for_comparison": request_data.erp_data,
            "instructions": "Carefully analyze the document image(s). Extract relevant header and line item information. "
                            "Compare the extracted information field by field against the 'erp_data_for_comparison'. "
                            "Report all discrepancies found, including value mismatches, fields missing in the document, or fields unexpectedly present in the document. "
                            "For each key field, provide your confidence in its extraction and verification. "
                            "Finally, provide an overall confidence score for the document verification.",
            "required_output_format": "Return a single JSON object strictly adhering to the following schema. Do not add any extra text or explanations outside this JSON object.",
            "output_json_schema_definition": output_schema_description
        }

    # Convert the prompt structure to JSON
    prompt_text = json.dumps(prompt_structure, indent=2)
    logger.debug(f"Gemini Verification Prompt for job {request_data.job_no}:\n{prompt_text}")

    # Build the parts for the Gemini request
    gemini_parts = _build_gemini_parts_from_request(prompt_text, request_data.document_images)

    # Configure generation parameters for better results
    generation_config = GenerationConfig(temperature=0.1, max_output_tokens=4096)

    # Try each model in sequence until one works
    last_error = None
    raw_response_text = None

    for model_name in model_names:
        try:
            logger.info(f"Attempting to use model: {model_name} for document verification")
            model = GenerativeModel(model_name)

            # All models in our list are vision-capable
            # Configure generation parameters for Gemini 2.0 models
            if "gemini-2.0" in model_name:
                # Gemini 2.0 models may need specific configuration
                generation_config = GenerationConfig(
                    temperature=0.1,
                    max_output_tokens=4096,
                    top_p=0.95,
                    top_k=40
                )

            # Use the full multimodal request for all models
            response = await model.generate_content_async(gemini_parts, generation_config=generation_config)

            raw_response_text = response.text
            logger.debug(f"Raw model response for verification (job {request_data.job_no}): {raw_response_text}")

            # Try to extract JSON from the response, handling potential text wrapping
            try:
                # First try direct JSON parsing
                llm_output_dict = json.loads(raw_response_text)
            except json.JSONDecodeError:
                # If that fails, try to extract JSON from the text
                import re
                json_match = re.search(r'({[\s\S]*})', raw_response_text)
                if json_match:
                    try:
                        llm_output_dict = json.loads(json_match.group(1))
                    except json.JSONDecodeError:
                        # If still failing, create a minimal response
                        logger.warning(f"Could not parse JSON from verification response. Creating minimal response.")
                        llm_output_dict = {
                            "discrepancies": [],
                            "field_confidences": [],
                            "overall_verification_confidence": 0.0
                        }
                else:
                    raise ValueError(f"Could not extract JSON from response: {raw_response_text}")

            # Convert the response to our schema format
            verification_response = schemas.VerificationResponse(
                discrepancies=[schemas.Discrepancy(**d) for d in llm_output_dict.get("discrepancies", [])],
                field_confidences=[schemas.FieldConfidence(**f) for f in llm_output_dict.get("field_confidences", [])],
                overall_verification_confidence=llm_output_dict.get("overall_verification_confidence", 0.0),
                raw_llm_response=raw_response_text
            )

            # If we get here, the model worked
            logger.info(f"Successfully used model: {model_name} for document verification")
            return verification_response

        except json.JSONDecodeError as e:
            last_error = e
            logger.warning(f"JSONDecodeError with model {model_name}: {e}")
            continue  # Try the next model
        except Exception as e:
            last_error = e
            logger.warning(f"Failed to use model {model_name} for verification: {e}")
            continue  # Try the next model

    # If we get here, all models failed
    error_message = f"All Gemini models failed for document verification. Last error: {str(last_error)}"
    logger.error(error_message)
    return schemas.VerificationResponse(
        error_message=error_message,
        raw_llm_response=raw_response_text
    )
