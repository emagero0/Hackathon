# Local Development Setup

This guide provides instructions for setting up and running both the Java backend and Python microservice locally for development.

## Prerequisites

- Java 17 or higher
- Python 3.10 or higher
- MySQL database
- Google Cloud Platform account with Gemini API access
- Service account with appropriate permissions

## Setting Up the Python Microservice

### 1. Configure the Environment

1. Navigate to the Python microservice directory:

```bash
cd src/gemini-python-service
```

2. Create a `.env` file from the example:

```bash
cp .env.example .env
```

3. Edit the `.env` file with your Google Cloud credentials and configuration:

```
# Google Cloud Project Configuration
GCP_PROJECT_ID=your-gcp-project-id
GCP_LOCATION=us-central1
MODEL_NAME=gemini-2.0-flash-001

# Google Cloud Service Account Credentials
# Option 1: Path to service account key file
GOOGLE_APPLICATION_CREDENTIALS=/path/to/your/service-account-key.json

# Option 2: Service account credentials as environment variables
GOOGLE_SERVICE_ACCOUNT_TYPE=service_account
GOOGLE_SERVICE_ACCOUNT_PROJECT_ID=your-gcp-project-id
GOOGLE_SERVICE_ACCOUNT_PRIVATE_KEY_ID=your-private-key-id
GOOGLE_SERVICE_ACCOUNT_PRIVATE_KEY="-----BEGIN PRIVATE KEY-----\nYour private key content here\n-----END PRIVATE KEY-----\n"
GOOGLE_SERVICE_ACCOUNT_CLIENT_EMAIL=your-service-account@your-project.iam.gserviceaccount.com
# ... other service account fields ...
```

### 2. Run the Python Microservice

#### On Windows:

```bash
run_local.bat
```

#### On Unix/Linux/Mac:

```bash
chmod +x run_local.sh
./run_local.sh
```

The Python microservice will start on http://localhost:8000.

You can verify it's running by accessing the health check endpoint:

```bash
curl http://localhost:8000/health
```

## Setting Up the Java Backend

### 1. Configure the Environment

1. Set the environment variable to use the local Python service:

#### On Windows:

```bash
set_local_python_service.bat
```

#### On Unix/Linux/Mac:

```bash
chmod +x set_local_python_service.sh
source ./set_local_python_service.sh
```

2. Configure the database connection in `src/backend/src/main/resources/application.properties`:

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/aierpdb?createDatabaseIfNotExist=true&useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
spring.datasource.username=${DB_USERNAME:root}
spring.datasource.password=${DB_PASSWORD}
```

3. Set the database password environment variable:

#### On Windows:

```bash
set DB_PASSWORD=your_mysql_password
```

#### On Unix/Linux/Mac:

```bash
export DB_PASSWORD=your_mysql_password
```

### 2. Run the Java Backend

```bash
cd src/backend
./mvnw spring-boot:run
```

The Java backend will start on http://localhost:8081.

## Testing the Setup

1. Verify the Python microservice is running:

```bash
curl http://localhost:8000/health
```

2. Verify the Java backend is running:

```bash
curl http://localhost:8081/api/health
```

3. Test the document verification flow:

```bash
curl -X GET "http://localhost:8081/api/verification/check-eligibility/J069026"
```

## Troubleshooting

### Python Microservice Issues

1. **Authentication Issues**:
   - Check your service account credentials in the `.env` file
   - Verify the service account has access to Vertex AI and Gemini models

2. **Model Access Issues**:
   - Ensure your project has the Vertex AI API enabled
   - Verify you have access to the Gemini models in your region

3. **Port Conflicts**:
   - If port 8000 is already in use, you can specify a different port:
     ```bash
     python run_local.py --port 8080
     ```

### Java Backend Issues

1. **Database Connection Issues**:
   - Verify MySQL is running
   - Check the database credentials
   - Ensure the database exists or `createDatabaseIfNotExist=true` is set

2. **Connection to Python Microservice**:
   - Verify the Python service is running
   - Check the `LLM_PYTHON_SERVICE_BASEURL` environment variable is set correctly
   - Try accessing the Python service directly to confirm it's accessible

3. **Port Conflicts**:
   - If port 8081 is already in use, you can change it in `application.properties`:
     ```properties
     server.port=8082
     ```

## Development Workflow

1. Start the Python microservice
2. Start the Java backend
3. Make changes to the code
4. The services will automatically reload when changes are detected
5. Test your changes

For more detailed information, refer to the README files in each service directory.
