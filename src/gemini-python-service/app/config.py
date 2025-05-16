from pydantic_settings import BaseSettings, SettingsConfigDict
from typing import Optional
import os

class Settings(BaseSettings):
    # Google Cloud Project Configuration
    gcp_project_id: str
    gcp_location: str
    gemini_model_name: str

    # Legacy path to service account key file (optional)
    google_application_credentials: Optional[str] = None

    # Google Cloud Service Account Credentials (from environment variables)
    google_service_account_type: Optional[str] = None
    google_service_account_project_id: Optional[str] = None
    google_service_account_private_key_id: Optional[str] = None
    google_service_account_private_key: Optional[str] = None
    google_service_account_client_email: Optional[str] = None
    google_service_account_client_id: Optional[str] = None
    google_service_account_auth_uri: Optional[str] = None
    google_service_account_token_uri: Optional[str] = None
    google_service_account_auth_provider_x509_cert_url: Optional[str] = None
    google_service_account_client_x509_cert_url: Optional[str] = None
    google_service_account_universe_domain: Optional[str] = None

    model_config = SettingsConfigDict(
        # Construct an absolute path to the .env file relative to this config.py file
        env_file=os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".env"),
        extra="ignore",
        env_file_encoding='utf-8'
    )

    def has_service_account_env_vars(self) -> bool:
        """Check if service account credentials are provided via environment variables"""
        return (self.google_service_account_type is not None and
                self.google_service_account_project_id is not None and
                self.google_service_account_private_key is not None and
                self.google_service_account_client_email is not None)

settings = Settings()
