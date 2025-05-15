from pydantic_settings import BaseSettings, SettingsConfigDict
from typing import Optional
import os

class Settings(BaseSettings):
    gcp_project_id: str
    gcp_location: str
    gemini_model_name: str
    google_application_credentials: Optional[str] = None # Path to service account key, if used

    model_config = SettingsConfigDict(
        # Construct an absolute path to the .env file relative to this config.py file
        env_file=os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".env"),
        extra="ignore",
        env_file_encoding='utf-8'
    )

settings = Settings()

# Example of how to use (can be removed if not needed):
# if __name__ == "__main__":
#     print("Settings loaded:")
#     print(f"  GCP Project ID: {settings.gcp_project_id}")
#     print(f"  GCP Location: {settings.gcp_location}")
#     print(f"  Gemini Model Name: {settings.gemini_model_name}")
#     print(f"  Google Application Credentials: {settings.google_application_credentials}")
