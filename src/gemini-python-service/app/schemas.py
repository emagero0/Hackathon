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

class ClassificationRequest(BaseModel):
    job_no: str
    document_images: List[DocumentImage]

class ClassifyAndVerifyRequest(BaseModel):
    job_no: str
    document_images: List[DocumentImage]
    erp_data: Dict[str, Any] # Represents the structured BC data for all document types

# --- Response Schemas ---

class IdentifierExtractionResponse(BaseModel):
    extracted_identifiers: Dict[str, str]
    error_message: Optional[str] = None # Optional field for errors

class Discrepancy(BaseModel):
    field_name: str
    document_value: Optional[str] = None # Made Optional
    erp_value: Optional[str] = None      # Made Optional
    severity: str = Field(default="medium")
    description: Optional[str] = None
    discrepancy_type: Optional[str] = None # Added

class FieldConfidence(BaseModel):
    field_name: str # Will contain prefixed names like "SalesQuote.CustomerName"
    extracted_value: Optional[str] = None
    extraction_confidence: float # Renamed from confidence
    match_assessment_confidence: Optional[float] = None # Added
    verified: bool = False # Likely defaults to False from Python now

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

class ClassificationResponse(BaseModel):
    document_type: str = Field(description="The classified document type (SalesQuote, ProformaInvoice, JobConsumption, or UNKNOWN)")
    confidence: float = Field(default=0.0, description="A confidence score between 0.0 and 1.0")
    reasoning: Optional[str] = Field(default=None, description="Brief explanation of why this classification was chosen")
    error_message: Optional[str] = None # Optional field for errors

class ClassifyAndVerifyResponse(BaseModel):
    document_type: str = Field(description="The classified document type (SalesQuote, ProformaInvoice, JobConsumption, or UNKNOWN)")
    classification_confidence: float = Field(default=0.0, description="A confidence score between 0.0 and 1.0 for classification")
    classification_reasoning: Optional[str] = Field(default=None, description="Brief explanation of why this classification was chosen")
    discrepancies: List[Discrepancy] = Field(default_factory=list)
    field_confidences: List[FieldConfidence] = Field(default_factory=list)
    overall_verification_confidence: float = 0.0
    raw_llm_response: Optional[str] = None
    error_message: Optional[str] = None
