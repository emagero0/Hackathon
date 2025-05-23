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


def _create_missing_identifier_error(document_type: str, missing_identifiers: list) -> str:
    """
    Creates user-friendly error messages for missing identifiers based on document type.
    """
    error_messages = []

    for identifier in missing_identifiers:
        if document_type.lower() == "salesquote":
            if "sales_quote_number" in identifier.lower() or "quote" in identifier.lower():
                error_messages.append("Cannot find Sales Quote Number from Sales Quote document")
        elif document_type.lower() == "proformainvoice":
            if "tax_invoice_number" in identifier.lower() or "invoice" in identifier.lower():
                error_messages.append("Cannot find Tax Invoice Number from Proforma Invoice document - please check Proforma Invoice")
        elif document_type.lower() == "jobconsumption":
            if "job_number" in identifier.lower() or "job" in identifier.lower():
                error_messages.append("Cannot find Job Number from Job Consumption document")
        else:
            # Generic message for unknown document types
            error_messages.append(f"Cannot find required identifier {identifier} from {document_type} document")

    return "; ".join(error_messages) if error_messages else f"Missing required identifiers from {document_type} document"


def _validate_extracted_identifiers(document_type: str, extracted_identifiers: dict) -> tuple[bool, str]:
    """
    Validates that required identifiers were extracted based on document type.
    Returns (is_valid, error_message).
    """
    required_identifiers = {
        "salesquote": ["sales_quote_number", "salesQuoteNo"],
        "proformainvoice": ["tax_invoice_number", "taxInvoiceNo"],
        "jobconsumption": ["job_number", "jobNo"]
    }

    doc_type_lower = document_type.lower()
    if doc_type_lower not in required_identifiers:
        return True, ""  # Unknown document type, skip validation

    required_fields = required_identifiers[doc_type_lower]
    missing_identifiers = []

    # Check if any of the required identifiers are present
    found_any = False
    for field in required_fields:
        if field in extracted_identifiers and extracted_identifiers[field]:
            found_any = True
            break

    if not found_any:
        missing_identifiers.extend(required_fields)

    if missing_identifiers:
        error_message = _create_missing_identifier_error(document_type, missing_identifiers)
        return False, error_message

    return True, ""


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
                        "discrepancy_type": "MISSING_IN_DOCUMENT | UNEXPECTED_IN_DOCUMENT | FORMAT_ERROR | VALUE_MISMATCH_FUZZY", # VALUE_MISMATCH for fuzzy only
                        "field_name": "string (e.g., 'header.Sell_to_Customer_Name', 'lines.0.Quantity')",
                        "expected_value": "any (especially for fuzzy matches)",
                        "actual_value": "any",
                        "description": "string (Describe why it's missing, unexpected, or details of a fuzzy mismatch. Backend generates most exact mismatches.)",
                        "confidence": "float (0.0-1.0, for LLM's confidence in this discrepancy finding)"
                    }
                ],
                "field_confidences": [
                    {
                        "field_name": "string (e.g., 'header.Sales_Quote_Number', 'lines.0.Description')",
                        "extracted_value": "any (The raw value extracted from the document)",
                        "extraction_confidence": "float (0.0-1.0, LLM's confidence in the accuracy of extracted_value)",
                        "match_assessment_confidence": "float (0.0-1.0, Optional: LLM's confidence if it performed a fuzzy match comparison against an expected value. Otherwise null.)"
                    }
                ],
                "overall_verification_confidence": "float (0.0-1.0, LLM's overall confidence in the extraction and any performed fuzzy matches)"
            },
            "headerFieldsToVerify": [
                {"documentFieldName": "Sales Quote Number", "instruction": "Extract the Sales Quote Number (usually format 'SQXXXXXX').", "erpPath": "salesQuoteHeader.No", "comparisonType": "exact_match_alphanumeric_only", "discrepancyFormat": "Sales Quote Header vs BC Mismatch: Document Sales Quote Number ('{actual}') != BC No ('{expected}')."},
                {"documentFieldName": "Customer Account Number", "instruction": "Extract the Customer Account Number.", "erpPath": "salesQuoteHeader.Sell_to_Customer_No", "comparisonType": "exact_match_alphanumeric_only", "discrepancyFormat": "Sales Quote Header vs BC Mismatch: Document Account No ('{actual}') != BC Sell_to_Customer_No ('{expected}')."},
                {"documentFieldName": "Customer Name", "instruction": "Extract the Customer Name. If expected data is available, assess fuzzy match.", "erpPath": "salesQuoteHeader.Sell_to_Customer_Name", "comparisonType": "fuzzy_match_ignore_case_whitespace", "discrepancyFormat": "Sales Quote Header vs BC Mismatch: Document Customer Name ('{actual}') != BC Sell_to_Customer_Name ('{expected}')."},
                {"documentFieldName": "Total Amount Including VAT", "instruction": "Extract the Total Amount Including VAT. Extract as a numeric string, preserving original format as much as possible (e.g. '22,200.00').", "erpPath": "salesQuoteHeader.Amount_Including_VAT", "comparisonType": "numeric_match_2_decimals", "discrepancyFormat": "Sales Quote Header vs BC Mismatch: Document Total Amount ('{actual}') != BC Amount_Including_VAT ('{expected}')."}
            ],
            "lineItemFieldsToVerify": [
                {"documentFieldName": "Description", "instruction": "Extract the line item Description. This is key for matching. If expected data is available, assess fuzzy match.", "erpPath": "Description", "comparisonType": "fuzzy_match_ignore_case_whitespace", "isKeyForMatching": True},
                {"documentFieldName": "Quantity", "instruction": "Extract the line item Quantity. Extract as a numeric string.", "erpPath": "Quantity", "comparisonType": "numeric_match_allow_integer_vs_decimal", "discrepancyFormat": "Sales Quote PDF vs BC Line Item '{matchedDescription}' Mismatch: Quantities differ (Document: {actual}, BC: {expected})."}
            ],
            "generalInstructions": [
                "Analyze the provided document images which represent a Sales Quote.",
                "For each field in 'headerFieldsToVerify' and 'lineItemFieldsToVerify', meticulously extract its value from the document based on the provided 'instruction'.",
                "Return the raw extracted value for each field. For numeric fields, extract the value as seen in the document, preserving original formatting as much as possible unless instructed otherwise.",
                "For fields with 'fuzzy_match_ignore_case_whitespace' comparisonType, if corresponding 'expectedErpData' is available, you may also provide a 'match_assessment_confidence' for your comparison.",
                "If you cannot find a field, report it in 'discrepancies' as 'MISSING_IN_DOCUMENT'.",
                "If you find clearly extra fields not specified for extraction but seem important, you may report them as 'UNEXPECTED_IN_DOCUMENT'.",
                "Match extracted line items to ERP line items primarily using 'Description' (fuzzy match).",
                "Report all findings in the JSON format defined in 'outputSchema'.",
                "Provide 'extraction_confidence' for every extracted field. This reflects your certainty about the accuracy of the extracted text/value itself.",
                "The backend service will perform most of the strict comparisons against ERP data using your extracted values."
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
                        "discrepancy_type": "MISSING_IN_DOCUMENT | UNEXPECTED_IN_DOCUMENT | FORMAT_ERROR | VALUE_MISMATCH_FUZZY",
                        "field_name": "string",
                        "expected_value": "any (for fuzzy matches)",
                        "actual_value": "any",
                        "description": "string (Describe missing/unexpected fields or fuzzy mismatch details. Backend handles most exact mismatches.)",
                        "confidence": "float (0.0-1.0, for LLM's confidence in this discrepancy finding)"
                    }
                ],
                "field_confidences": [
                    {
                        "field_name": "string",
                        "extracted_value": "any (Raw extracted value)",
                        "extraction_confidence": "float (0.0-1.0, LLM's confidence in extracted_value accuracy)",
                        "match_assessment_confidence": "float (0.0-1.0, Optional: LLM's confidence for fuzzy match comparison. Null otherwise.)"
                    }
                ],
                "overall_verification_confidence": "float (0.0-1.0, LLM's overall confidence in extraction and any fuzzy matches)"
            },
            "headerFieldsToVerify": [
                {"documentFieldName": "Proforma Invoice Number", "instruction": "Extract the Proforma Invoice Number.", "erpPath": "salesInvoiceHeader.No", "comparisonType": "exact_match_alphanumeric_only", "discrepancyFormat": "Proforma Invoice Header vs BC Mismatch: Document Proforma Invoice Number ('{actual}') != BC No ('{expected}')."},
                {"documentFieldName": "Customer Account Number", "instruction": "Extract the Customer Account Number.", "erpPath": "salesInvoiceHeader.Sell_to_Customer_No", "comparisonType": "exact_match_alphanumeric_only", "discrepancyFormat": "Proforma Invoice Header vs BC Mismatch: Document Account No ('{actual}') != BC Sell_to_Customer_No ('{expected}')."},
                {"documentFieldName": "Customer Name", "instruction": "Extract the Customer Name. If expected data is available, assess fuzzy match.", "erpPath": "salesInvoiceHeader.Sell_to_Customer_Name", "comparisonType": "fuzzy_match_ignore_case_whitespace", "discrepancyFormat": "Proforma Invoice Header vs BC Mismatch: Document Customer Name ('{actual}') != BC Sell_to_Customer_Name ('{expected}')."}
                # Add other header fields like Total Amount if applicable for Proforma, focusing on extraction.
            ],
            "lineItemFieldsToVerify": [
                {"documentFieldName": "Description", "instruction": "Extract the line item Description. Key for matching. If expected data is available, assess fuzzy match.", "erpPath": "Description", "comparisonType": "fuzzy_match_ignore_case_whitespace", "isKeyForMatching": True},
                {"documentFieldName": "Quantity", "instruction": "Extract the line item Quantity as a numeric string.", "erpPath": "Quantity", "comparisonType": "numeric_match_allow_integer_vs_decimal", "discrepancyFormat": "Proforma Invoice PDF vs BC Line Item '{matchedDescription}' Mismatch: Quantities differ (Document: {actual}, BC: {expected})."}
                # Add other line item fields like Unit Price, Total Price if applicable, focusing on extraction.
            ],
            "generalInstructions": [
                "Analyze the provided document images which represent a Proforma Invoice.",
                "For each field in 'headerFieldsToVerify' and 'lineItemFieldsToVerify', meticulously extract its value from the document based on the 'instruction'.",
                "Return the raw extracted value. For numeric fields, extract as seen, preserving format.",
                "For fields with 'fuzzy_match_ignore_case_whitespace', if 'expectedErpData' is available, provide 'match_assessment_confidence'.",
                "Report 'MISSING_IN_DOCUMENT' or 'UNEXPECTED_IN_DOCUMENT' discrepancies as appropriate.",
                "Match line items using 'Description' (fuzzy match).",
                "Report all findings in the JSON format defined in 'outputSchema'.",
                "Provide 'extraction_confidence' for every extracted field.",
                "The backend service will perform most strict comparisons."
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
                        "discrepancy_type": "MISSING_IN_DOCUMENT | UNEXPECTED_IN_DOCUMENT | FORMAT_ERROR | VALUE_MISMATCH_FUZZY",
                        "field_name": "string",
                        "expected_value": "any (for fuzzy matches)",
                        "actual_value": "any",
                        "description": "string (Describe missing/unexpected fields or fuzzy mismatch details. Backend handles most exact mismatches.)",
                        "confidence": "float (0.0-1.0, for LLM's confidence in this discrepancy finding)"
                    }
                ],
                "field_confidences": [
                    {
                        "field_name": "string (e.g. 'header.Job_Number', 'line.0.Description', 'additional.ReceivedBy_SignaturePresence')",
                        "extracted_value": "any (Raw extracted value, or finding like 'Signature found')",
                        "extraction_confidence": "float (0.0-1.0, LLM's confidence in extracted_value/finding accuracy)",
                        "match_assessment_confidence": "float (0.0-1.0, Optional: LLM's confidence for fuzzy match comparison. Null otherwise.)"
                    }
                ],
                "overall_verification_confidence": "float (0.0-1.0, LLM's overall confidence in extraction and any fuzzy matches/semantic checks)"
            },
            "headerFieldsToVerify": [
                {"documentFieldName": "Job Number", "instruction": "Extract the Job Number from the document.", "erpPath": "requestContext.jobId", "comparisonType": "exact_match_alphanumeric_only", "discrepancyFormat": "Job Consumption Header vs BC Mismatch: Document Job Number ('{actual}') != Expected Job No ('{expected}')."}
            ],
            "lineItemFieldsToVerify": [
                {"documentFieldName": "Description", "instruction": "Extract the line item Description. Key for matching. If expected data is available, assess fuzzy match.", "erpPath": "Description", "comparisonType": "fuzzy_match_ignore_case_whitespace", "isKeyForMatching": True},
                {"documentFieldName": "Quantity", "instruction": "Extract the line item Quantity as a numeric string.", "erpPath": "Quantity", "comparisonType": "numeric_match_allow_integer_vs_decimal", "discrepancyFormat": "Job Consumption PDF vs BC Line Item '{matchedDescription}' Mismatch: Quantities differ (Document: {actual}, BC: {expected})."},
                {"documentFieldName": "Item/Resource No", "instruction": "Extract the Item or Resource Number for the line item, if present.", "erpPath": "No", "comparisonType": "exact_match_alphanumeric_only", "discrepancyFormat": "Job Consumption PDF vs BC Line Item '{matchedDescription}' Mismatch: Item/Resource No differs (Document: {actual}, BC: {expected}).", "optionalInDocument": True},
                {"documentFieldName": "Type", "instruction": "Extract the Type for the line item (e.g., Item, Resource), if present.", "erpPath": "Type", "comparisonType": "exact_match_ignore_case", "discrepancyFormat": "Job Consumption PDF vs BC Line Item '{matchedDescription}' Mismatch: Type differs (Document: {actual}, BC: {expected}).", "optionalInDocument": True}
            ],
            "additionalVerifications": [
                {"fieldName": "ReceivedBy_SignaturePresence", "instruction": "Check if the 'Received By' section contains a signature OR a printed name. Report your finding as 'Signature found', 'Printed name found: [name]', 'Signature and printed name found: [name]', or 'Signature/Name not found'.", "discrepancyFormat": "Job Consumption PDF Missing Signature/Name: The 'Received By' section does not appear to be signed or contain a printed name."}
            ],
            "generalInstructions": [
                "Analyze the provided document images which represent a Job Consumption document.",
                "For each field in 'headerFieldsToVerify' and 'lineItemFieldsToVerify', meticulously extract its value from the document based on the 'instruction'.",
                "For the check in 'additionalVerifications', report your finding and confidence in 'field_confidences' using the specified 'fieldName'.",
                "Return raw extracted values. For numeric fields, extract as seen, preserving format.",
                "For fields with 'fuzzy_match_ignore_case_whitespace', if 'expectedErpData' is available, provide 'match_assessment_confidence'.",
                "Report 'MISSING_IN_DOCUMENT' or 'UNEXPECTED_IN_DOCUMENT' discrepancies as appropriate.",
                "Match line items using 'Description' (fuzzy match).",
                "Report all findings in the JSON format defined in 'outputSchema'.",
                "Provide 'extraction_confidence' for every extracted field and performed check.",
                "The backend service will perform most strict comparisons."
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

def _build_classify_and_verify_prompt(request_data: schemas.ClassifyAndVerifyRequest) -> Dict[str, Any]:
    """Build a combined prompt for document classification and verification."""

    # Check if this is the initial classification step or the verification step
    is_initial_classification = "jobNo" in request_data.erp_data and len(request_data.erp_data) == 1

    if is_initial_classification:
        # This is the initial classification step - focus on identifying document type and extracting key identifiers
        prompt = """
You are given one or more business documents.

Your tasks are:

Identify the type of each document. Possible types include:

Sales Quote

Proforma Invoice

Job Shipment

Extract the most relevant unique identifier from each document:

For a Sales Quote, extract the Sales Quote Number (e.g., SQ1007475)

For a Proforma Invoice, extract the Tax Invoice Number (e.g., 1595499)

For a Job Shipment, extract the Job Shipment Number (e.g., JC149529)

Return the result in a single JSON array, with one object per document, using the following structure:

[
  {
    "document_type": "Sales Quote",
    "identifier_label": "Sales Quote Number",
    "identifier_value": "SQ1007475"
  },
  {
    "document_type": "Proforma Invoice",
    "identifier_label": "Tax Invoice Number",
    "identifier_value": "1595499"
  },
  {
    "document_type": "Job Shipment",
    "identifier_label": "Job Shipment Number",
    "identifier_value": "JC149529"
  }
]
"""
    else:
        # This is the verification step - focus on verifying the document against ERP data
        prompt = {
            "system_context": "You are an AI document verification assistant specialized in analyzing business documents and comparing them against ERP system data.",
            "task_description": "Verify the document against the appropriate ERP data.",
            "job_number_context": request_data.job_no,
            "erp_data_for_comparison": request_data.erp_data,
            "document_types": {
                "SalesQuote": {
                    "description": "A document that provides pricing for products or services before a sale is finalized.",
                    "key_identifiers": [
                        "Contains 'SALES QUOTE' in the header or title",
                        "Has a quote number or reference",
                        "Lists items with quantities and prices",
                        "May include terms and conditions",
                        "Does not indicate that payment has been made"
                    ],
                    "erp_data_mapping": {
                        "header": "salesQuoteHeader",
                        "lines": "salesQuoteLines",
                        "key_fields": [
                            {"document_field": "Quote No", "erp_field": "No"},
                            {"document_field": "Customer Name", "erp_field": "Sell_to_Customer_Name"},
                            {"document_field": "Total Amount", "erp_field": "Amount_Including_VAT"}
                        ]
                    }
                },
                "ProformaInvoice": {
                    "description": "A preliminary bill of sale sent to buyers before a shipment or delivery of goods.",
                    "key_identifiers": [
                        "Contains 'PRO FORMA INVOICE' in the header or title",
                        "Contains text like 'This is not a Tax Invoice'",
                        "Has an invoice number",
                        "Lists items with quantities and prices",
                        "May include payment terms"
                    ],
                    "erp_data_mapping": {
                        "header": "salesInvoiceHeader",
                        "lines": "salesInvoiceLines",
                        "key_fields": [
                            {"document_field": "Invoice No", "erp_field": "No"},
                            {"document_field": "Customer Name", "erp_field": "Sell_to_Customer_Name"},
                            {"document_field": "Amount", "erp_field": "Amount"}
                        ]
                    }
                },
                "JobConsumption": {
                    "description": "A document that details materials or services used for a specific job or project.",
                    "key_identifiers": [
                        "Contains 'JOB SHIPMENT' in the header or title",
                        "Has a 'Job Shipment No' field",
                        "Lists materials or services consumed",
                        "May reference a specific job number",
                        "Often includes quantities and costs of materials used"
                    ],
                    "erp_data_mapping": {
                        "entries": "jobLedgerEntries",
                        "key_fields": [
                            {"document_field": "Job No", "erp_field": "Job_No"},
                            {"document_field": "Description", "erp_field": "Description"},
                            {"document_field": "Quantity", "erp_field": "Quantity"}
                        ]
                    }
                }
            },
            "instructions": [
                "STEP 1: Analyze the provided document images and confirm the document type.",
                "Determine which document type it matches based on the characteristics listed.",
                "Identify the document type as one of: 'SalesQuote', 'ProformaInvoice', or 'JobConsumption'.",
                "If you cannot confidently classify the document, use 'UNKNOWN'.",
                "",
                "STEP 2: Verify the document against the appropriate ERP data.",
                "Extract relevant header and line item information.",
                "Use the erp_data_mapping to identify which fields to compare between the document and ERP data.",
                "Compare the extracted information field by field against the appropriate section of the ERP data.",
                "Report all discrepancies found, including value mismatches, fields missing in the document, or fields unexpectedly present in the document.",
                "For each field you extract, provide a confidence score indicating how certain you are about the extraction.",
                "Pay special attention to the key fields listed in the erp_data_mapping.",
                "Finally, provide an overall verification confidence score."
            ],
            "output_format": {
                "documentType": "The classified document type (SalesQuote, ProformaInvoice, JobConsumption, or UNKNOWN)",
                "classificationConfidence": "A confidence score between 0.0 and 1.0 for the classification",
                "classificationReasoning": "Brief explanation of why this classification was chosen",
                "discrepancies": [
                    {
                        "field_name": "Name of the field with a discrepancy",
                        "document_value": "Value found in the document",
                        "erp_value": "Value from the ERP data",
                        "severity": "high/medium/low",
                        "description": "Optional explanation of the discrepancy"
                    }
                ],
                "fieldConfidences": [
                    {
                        "field_name": "Name of the extracted field",
                        "confidence": "Confidence score between 0.0 and 1.0",
                        "extracted_value": "The value extracted from the document",
                        "verified": "Boolean indicating if the field matches the ERP data"
                    }
                ],
                "overallVerificationConfidence": "A score between 0.0 and 1.0 indicating overall confidence in the verification"
            }
        }

    return prompt

async def classify_and_verify_with_gemini(
    request_data: schemas.ClassifyAndVerifyRequest
) -> schemas.ClassifyAndVerifyResponse:
    """
    Classifies a document and then verifies it against ERP data using Gemini.

    This function has been enhanced to support two modes:
    1. Initial classification mode: When erp_data only contains jobNo, it focuses on extracting document identifiers
    2. Verification mode: When erp_data contains Business Central data, it performs full verification
    """
    if not _vertex_ai_initialized:
        logger.error("Vertex AI not initialized. Cannot process request.")
        return schemas.ClassifyAndVerifyResponse(
            document_type="UNKNOWN",
            classification_confidence=0.0,
            error_message="Vertex AI client not initialized."
        )

    # Determine if this is the initial classification step or the verification step
    is_initial_classification = "jobNo" in request_data.erp_data and len(request_data.erp_data) == 1

    if is_initial_classification:
        logger.info(f"Running in INITIAL CLASSIFICATION mode for job {request_data.job_no} - focusing on document type and identifier extraction")
    else:
        logger.info(f"Running in VERIFICATION mode for job {request_data.job_no} - performing full verification with Business Central data")

    # List of model names to try in order of preference
    model_names = [
        settings.gemini_model_name,      # Try the configured model first (gemini-2.0-flash-001)
        "gemini-2.0-flash-lite-001",     # Gemini 2.0 Flash Lite model as fallback
    ]

    # Prepare the prompt
    prompt = _build_classify_and_verify_prompt(request_data)

    # Prepare the images
    image_parts = []
    for img_data in request_data.document_images:
        try:
            # Decode base64 image
            image_bytes = base64.b64decode(img_data.image_base64)
            image_parts.append(Part.from_data(mime_type=img_data.mime_type, data=image_bytes))
        except Exception as e:
            logger.error(f"Error processing image: {e}")
            continue

    if not image_parts:
        logger.error("No valid images provided for classification and verification")
        return schemas.ClassifyAndVerifyResponse(
            document_type="UNKNOWN",
            classification_confidence=0.0,
            error_message="No valid images provided"
        )

    # Prepare the parts for the model
    if is_initial_classification:
        # For initial classification, use the prompt as a string directly
        prompt_text = prompt
        logger.info(f"Prompt for job {request_data.job_no}: {prompt_text[:200]}...")
    else:
        # For verification, convert the prompt to a JSON string
        prompt_text = json.dumps(prompt, indent=2)
        logger.info(f"Prompt for job {request_data.job_no}: {prompt_text[:200]}...")

    gemini_parts = [
        Part.from_text(prompt_text),
        *image_parts
    ]

    # Configure generation parameters - use different settings based on the mode
    if is_initial_classification:
        # For initial classification, we need accurate extraction of document numbers
        generation_config = GenerationConfig(
            temperature=0.1,
            max_output_tokens=2048,
            top_p=0.95,
            top_k=40
        )
    else:
        # For verification, we need more detailed analysis
        generation_config = GenerationConfig(
            temperature=0.1,
            max_output_tokens=4096,
            top_p=0.95,
            top_k=40
        )

    last_error = None
    raw_response_text = ""

    for model_name in model_names:
        try:
            logger.info(f"Attempting to use model: {model_name} for document classification and verification")
            model = GenerativeModel(model_name)

            # Use the full multimodal request
            response = await model.generate_content_async(gemini_parts, generation_config=generation_config)
            raw_response_text = response.text
            logger.debug(f"Raw model response for classification and verification (job {request_data.job_no}): {raw_response_text}")

            # Try to parse the JSON response
            try:
                # First try to extract JSON from the response if it's not already in JSON format
                import re
                json_match = re.search(r'```json\s*([\s\S]*?)\s*```', raw_response_text)
                if json_match:
                    json_str = json_match.group(1)
                    llm_output_dict = json.loads(json_str)
                else:
                    # Try to parse the whole response as JSON
                    llm_output_dict = json.loads(raw_response_text)

                # Convert the response to our schema format
                if is_initial_classification:
                    # Handle the new array format for initial classification
                    # Check if the response is an array
                    if isinstance(llm_output_dict, list):
                        documents_array = llm_output_dict
                    else:
                        # If it's not an array, try to wrap it in an array
                        documents_array = [llm_output_dict]

                    logger.info(f"Parsed {len(documents_array)} documents from LLM response")

                    # Create field confidences based on all extracted identifiers
                    field_confidences = []
                    document_types = []
                    classification_reasoning = []

                    for doc in documents_array:
                        document_type = doc.get("document_type", "UNKNOWN")
                        identifier_label = doc.get("identifier_label", "")
                        identifier_value = doc.get("identifier_value", "")

                        # Map the document type to our expected format
                        normalized_type = "UNKNOWN"
                        if "sales quote" in document_type.lower():
                            normalized_type = "SalesQuote"
                        elif "proforma invoice" in document_type.lower():
                            normalized_type = "ProformaInvoice"
                        elif "job shipment" in document_type.lower():
                            normalized_type = "JobConsumption"

                        document_types.append(normalized_type)
                        classification_reasoning.append(f"{document_type} with {identifier_label}: {identifier_value}")

                        if identifier_value:
                            # Determine field name based on identifier label
                            field_name = "Unknown"
                            if "quote" in identifier_label.lower():
                                field_name = "Quote No"
                            elif "invoice" in identifier_label.lower() or "tax" in identifier_label.lower():
                                field_name = "Invoice No"
                            elif "shipment" in identifier_label.lower() or "job" in identifier_label.lower():
                                field_name = "Job Shipment No"

                            field_confidences.append(
                                schemas.FieldConfidence(
                                    field_name=field_name,
                                    confidence=0.9,
                                    extracted_value=identifier_value,
                                    verified=False
                                )
                            )

                    # Determine the primary document type (for backward compatibility)
                    primary_document_type = "UNKNOWN"
                    if "SalesQuote" in document_types:
                        primary_document_type = "SalesQuote"
                    elif "ProformaInvoice" in document_types:
                        primary_document_type = "ProformaInvoice"
                    elif "JobConsumption" in document_types:
                        primary_document_type = "JobConsumption"

                    response_data = schemas.ClassifyAndVerifyResponse(
                        document_type=primary_document_type,
                        classification_confidence=0.9,
                        classification_reasoning=f"Documents identified: {', '.join(classification_reasoning)}",
                        field_confidences=field_confidences,
                        overall_verification_confidence=0.0,
                        raw_llm_response=raw_response_text
                    )
                else:
                    # Use the original format for verification
                    response_data = schemas.ClassifyAndVerifyResponse(
                        document_type=llm_output_dict.get("documentType", "UNKNOWN"),
                        classification_confidence=llm_output_dict.get("classificationConfidence", 0.0),
                        classification_reasoning=llm_output_dict.get("classificationReasoning", "No reasoning provided"),
                        discrepancies=[
                            schemas.Discrepancy(**d) for d in llm_output_dict.get("discrepancies", [])
                        ],
                        field_confidences=[
                            schemas.FieldConfidence(**f) for f in llm_output_dict.get("fieldConfidences", [])
                        ],
                        overall_verification_confidence=llm_output_dict.get("overallVerificationConfidence", 0.0),
                        raw_llm_response=raw_response_text
                    )

                # Log the extracted identifiers in initial classification mode
                if is_initial_classification and response_data.field_confidences:
                    extracted_identifiers = {}
                    for field in response_data.field_confidences:
                        if field.extracted_value:
                            extracted_identifiers[field.field_name] = field.extracted_value

                    if extracted_identifiers:
                        logger.info(f"Extracted identifiers for job {request_data.job_no}: {extracted_identifiers}")
                    else:
                        logger.warning(f"No identifiers extracted for job {request_data.job_no}")

                # If we get here, the model worked
                logger.info(f"Successfully used model: {model_name} for document classification and verification")
                return response_data

            except json.JSONDecodeError as e:
                logger.warning(f"JSONDecodeError with model {model_name}: {e}")

                # Try to extract information from text response
                document_type = "UNKNOWN"
                if "salesquote" in raw_response_text.lower() or "sales quote" in raw_response_text.lower():
                    document_type = "SalesQuote"
                elif "proformainvoice" in raw_response_text.lower() or "proforma invoice" in raw_response_text.lower():
                    document_type = "ProformaInvoice"
                elif "jobconsumption" in raw_response_text.lower() or "job consumption" in raw_response_text.lower() or "job shipment" in raw_response_text.lower():
                    document_type = "JobConsumption"

                # Try to extract document numbers if in initial classification mode
                field_confidences = []
                if is_initial_classification:
                    # Try to find JSON array in the text
                    array_match = re.search(r'\[\s*\{[^]]*\}\s*\]', raw_response_text)
                    if array_match:
                        try:
                            # Try to parse the JSON array
                            json_array = json.loads(array_match.group(0))
                            logger.info(f"Successfully extracted JSON array with {len(json_array)} items from text response")

                            # Process each item in the array
                            for item in json_array:
                                doc_type = item.get("document_type", "")
                                identifier_label = item.get("identifier_label", "")
                                identifier_value = item.get("identifier_value", "")

                                if identifier_value:
                                    field_name = "Unknown"
                                    if "quote" in identifier_label.lower():
                                        field_name = "Quote No"
                                    elif "invoice" in identifier_label.lower() or "tax" in identifier_label.lower():
                                        field_name = "Invoice No"
                                    elif "shipment" in identifier_label.lower() or "job" in identifier_label.lower():
                                        field_name = "Job Shipment No"

                                    field_confidences.append(schemas.FieldConfidence(
                                        field_name=field_name,
                                        confidence=0.8,
                                        extracted_value=identifier_value,
                                        verified=False
                                    ))

                            # If we successfully extracted fields, determine document type
                            if field_confidences:
                                # Use the first document type found
                                first_doc = json_array[0]
                                doc_type = first_doc.get("document_type", "UNKNOWN")
                                if "sales quote" in doc_type.lower():
                                    document_type = "SalesQuote"
                                elif "proforma invoice" in doc_type.lower():
                                    document_type = "ProformaInvoice"
                                elif "job shipment" in doc_type.lower():
                                    document_type = "JobConsumption"
                                else:
                                    document_type = "UNKNOWN"

                                # Return early with the extracted data
                                return schemas.ClassifyAndVerifyResponse(
                                    document_type=document_type,
                                    classification_confidence=0.8,
                                    classification_reasoning=f"Extracted from partial JSON: {doc_type}",
                                    field_confidences=field_confidences,
                                    raw_llm_response=raw_response_text
                                )
                        except json.JSONDecodeError:
                            logger.warning("Found JSON-like array but failed to parse it")

                    # Fallback to regex extraction if JSON parsing failed
                    document_type = "UNKNOWN"
                    if "sales quote" in raw_response_text.lower():
                        document_type = "SalesQuote"
                    elif "proforma invoice" in raw_response_text.lower():
                        document_type = "ProformaInvoice"
                    elif "job shipment" in raw_response_text.lower():
                        document_type = "JobConsumption"

                    # Try to extract sales quote number (look for SQ followed by numbers)
                    quote_match = re.search(r'(?:sales\s*quote\s*number|quote\s*no|sq)[:\s]*([A-Za-z0-9]+)', raw_response_text, re.IGNORECASE)
                    if quote_match or document_type == "SalesQuote":
                        # If we found a quote number or the document is a sales quote, extract the identifier
                        identifier_value = quote_match.group(1) if quote_match else ""
                        # Look for SQ pattern if not found in the first regex
                        if not identifier_value:
                            sq_match = re.search(r'(SQ\d+)', raw_response_text)
                            identifier_value = sq_match.group(1) if sq_match else ""

                        if identifier_value:
                            field_confidences.append(schemas.FieldConfidence(
                                field_name="Quote No",
                                confidence=0.7,
                                extracted_value=identifier_value,
                                verified=False
                            ))

                    # Try to extract invoice number
                    invoice_match = re.search(r'(?:tax\s*invoice\s*number|invoice\s*no|proforma)[:\s]*(\d+)', raw_response_text, re.IGNORECASE)
                    if invoice_match or document_type == "ProformaInvoice":
                        # If we found an invoice number or the document is a proforma invoice, extract the identifier
                        identifier_value = invoice_match.group(1) if invoice_match else ""
                        if not identifier_value:
                            # Try another pattern for invoice numbers
                            inv_match = re.search(r'(\d{6,})', raw_response_text)
                            identifier_value = inv_match.group(1) if inv_match else ""

                        if identifier_value:
                            field_confidences.append(schemas.FieldConfidence(
                                field_name="Invoice No",
                                confidence=0.7,
                                extracted_value=identifier_value,
                                verified=False
                            ))

                    # Try to extract job shipment number
                    job_match = re.search(r'(?:job\s*shipment\s*no|job\s*shipment\s*number|jc)[:\s]*([A-Za-z0-9]+)', raw_response_text, re.IGNORECASE)
                    if job_match or document_type == "JobConsumption":
                        identifier_value = job_match.group(1) if job_match else ""
                        if not identifier_value:
                            # Look for JC pattern
                            jc_match = re.search(r'(JC\d+)', raw_response_text)
                            identifier_value = jc_match.group(1) if jc_match else ""

                        if identifier_value:
                            field_confidences.append(schemas.FieldConfidence(
                                field_name="Job Shipment No",
                                confidence=0.7,
                                extracted_value=identifier_value,
                                verified=False
                            ))

                # Validate extracted identifiers for business logic errors
                if is_initial_classification and document_type != "UNKNOWN":
                    # Convert field_confidences to a dictionary for validation
                    extracted_identifiers = {}
                    for field in field_confidences:
                        if field.extracted_value:
                            extracted_identifiers[field.field_name] = field.extracted_value

                    # Validate that required identifiers were extracted
                    is_valid, validation_error = _validate_extracted_identifiers(document_type, extracted_identifiers)
                    if not is_valid:
                        # Return business logic error for missing identifiers
                        return schemas.ClassifyAndVerifyResponse(
                            document_type=document_type,
                            classification_confidence=0.5,
                            classification_reasoning="Document classified but missing required identifiers",
                            field_confidences=field_confidences,
                            error_message=validation_error,
                            raw_llm_response=raw_response_text
                        )

                # Create a basic response with the extracted document type and any extracted fields
                return schemas.ClassifyAndVerifyResponse(
                    document_type=document_type,
                    classification_confidence=0.5,
                    classification_reasoning="Extracted from text response",
                    field_confidences=field_confidences,
                    raw_llm_response=raw_response_text
                )

        except Exception as e:
            last_error = e
            logger.warning(f"Failed to use model {model_name} for classification and verification: {e}")
            continue  # Try the next model

    # If we get here, all models failed
    error_message = f"All Gemini models failed for classification and verification. Last error: {str(last_error)}"
    logger.error(error_message)
    return schemas.ClassifyAndVerifyResponse(
        document_type="UNKNOWN",
        classification_confidence=0.0,
        error_message=error_message,
        raw_llm_response=raw_response_text
    )

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

    # Use the comprehensive cross-document verification prompt
    document_type = request_data.document_type.lower()

    # Check if this is a combined verification request (multiple documents)
    if document_type == "combined" or document_type == "all":
        logger.warning(f"Attempted to call verify_document_with_gemini with document_type '{request_data.document_type}'. Cross-document verification is now orchestrated by the backend. This path should not be directly called for combined checks and will return an empty response.")
        return schemas.VerificationResponse(
            error_message="Cross-document verification is orchestrated by the backend. This path should not be directly called for combined checks.",
            discrepancies=[],
            field_confidences=[],
            overall_verification_confidence=0.0
        )
    else:
        # Use the existing document-specific prompts for individual document verification
        prompt_structure = None # Initialize prompt_structure
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

    # Handle the prompt appropriately based on type
    if isinstance(prompt_structure, str):
        # If it's already a string (for combined verification), use it directly
        prompt_text = prompt_structure
        logger.debug(f"Using text prompt for combined verification for job {request_data.job_no}")
    else:
        # Otherwise, convert the prompt structure to JSON
        prompt_text = json.dumps(prompt_structure, indent=2)
        logger.debug(f"Gemini Verification Prompt for job {request_data.job_no}:\n{prompt_text[:500]}...")

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
