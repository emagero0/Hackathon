from pydantic import BaseModel, Field
from typing import List, Dict, Any, Optional

class DocumentImage(BaseModel):
    image_base64: str = Field(description="Base64 encoded string of the document image.")
    mime_type: str = Field(default="image/png", description="MIME type of the image (e.g., image/png, image/jpeg).")

class IdentifierExtractionRequest(BaseModel):
    job_no: str
    document_type: str
    document_images: List[DocumentImage]

class VerificationRequest(BaseModel):
    job_no: str
    document_type: str
    document_images: List[DocumentImage]
    erp_data: Dict[str, Any] # Represents the structured BC data

# --- Response Schemas ---

class IdentifierExtractionResponse(BaseModel):
    extracted_identifiers: Dict[str, str]
    error_message: Optional[str] = None # Optional field for errors

class Discrepancy(BaseModel):
    discrepancy_type: str
    field_name: str
    expected_value: Optional[Any] = None # Making optional as per original Java DTOs might imply nulls
    actual_value: Optional[Any] = None   # Making optional
    description: str
    confidence: Optional[float] = None # Making optional

class FieldConfidence(BaseModel):
    field_name: str
    extracted_value: Optional[Any] = None # Making optional
    verification_confidence: Optional[float] = None # Making optional
    extraction_confidence: Optional[float] = None # Making optional

class ExtractedValues(BaseModel): # Placeholder if you want to return all extracted values
    header: Dict[str, Any] = {}
    lines: List[Dict[str, Any]] = []

class VerificationResponse(BaseModel):
    # Based on GeminiVerificationResult.java structure, adapted for Pydantic
    discrepancies: List[Discrepancy] = Field(default_factory=list)
    field_confidences: List[FieldConfidence] = Field(default_factory=list)
    # extracted_values: Optional[ExtractedValues] = None # If you decide to include this
    overall_verification_confidence: float = 0.0
    raw_llm_response: Optional[str] = None # For debugging
    error_message: Optional[str] = None # Optional field for errors
