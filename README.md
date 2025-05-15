# ERP Document Verification Module

This project implements a module for verifying ERP job documents by comparing data between Microsoft Dynamics 365 Business Central (BC) Job Ledger Entries and associated PDF documents (Job Consumption, Proforma Invoice, Sales Quote). It features a web frontend for monitoring and manually triggering verifications, and a backend service that handles the verification logic asynchronously.

## Features

*   **Business Central Integration:** Fetches Job Ledger data via BC OData API.
*   **Document Verification with AI:**
    *   Uses Google Gemini 2.0 models (gemini-2.0-flash-001 and gemini-2.0-flash-lite-001) for document verification.
    *   Extracts key identifiers from documents (Sales Quote Number, Proforma Invoice Number, etc.).
    *   Performs comprehensive verification of document content against Business Central data.
    *   Verifies header fields (document numbers, customer information) and line items (descriptions, quantities).
    *   Checks for presence of signatures in the Job Consumption document.
*   **Microservice Architecture:**
    *   Java Spring Boot backend for business logic and data persistence.
    *   Python FastAPI microservice for AI-powered document verification using Google Gemini.
    *   Communication between services via REST APIs.
*   **Asynchronous Processing:** Uses Spring's @Async annotation for background processing of verification requests.
*   **Web Interface (React/TypeScript):**
    *   Dashboard displaying overall job statistics and recent activity.
    *   Jobs list page.
    *   Job detail page showing verification status and discrepancies found.
    *   **Job Verification Page (for Managers):** Allows checking job eligibility for verification, adding eligible jobs to a list, and triggering batch verification.
    *   Manual verification trigger via dialog (on Job Detail page).
    *   Real-time chart showing daily verification status breakdown.
    *   Role-based access control (e.g., Job Verification page visible only to `verification_manager`).
    *   Light/Dark mode theme support.
*   **Database Persistence:** Stores Job status and detailed Verification Request history (including discrepancies) in a MySQL database using Spring Data JPA.
*   **Activity Logging:** Records key system events (verification start, completion, errors) for monitoring.

## Technology Stack

### Frontend
-   **Framework/Language:** React with TypeScript
-   **Build Tool:** Vite
-   **Styling:** Tailwind CSS
-   **Component Library:** shadcn/ui
-   **Routing:** React Router (`react-router-dom`)
-   **API Client:** Axios
-   **Charting:** Recharts
-   **Date Formatting:** `date-fns`
-   **Notifications:** `sonner`
-   **Package Manager:** pnpm

### Java Backend
-   **Framework/Language:** Java 17+ with Spring Boot 3.2+
-   **Build Tool:** Maven
-   **Database:** MySQL 8.0+
-   **Data Persistence:** Spring Data JPA / Hibernate
-   **API:** Spring Web MVC (REST Controllers)
-   **Asynchronous Processing:** Spring's @Async annotation
-   **External API Client:** Spring WebFlux (`WebClient`) for Business Central OData API
-   **PDF Processing:** Apache PDFBox
-   **JSON Handling:** Jackson
-   **Logging:** SLF4j / Logback
-   **Utilities:** Lombok

### Python AI Service
-   **Framework/Language:** Python 3.10+ with FastAPI
-   **AI Models:** Google Gemini 2.0 (gemini-2.0-flash-001, gemini-2.0-flash-lite-001)
-   **API Client:** Google Cloud Vertex AI SDK
-   **Image Processing:** Pillow
-   **JSON Handling:** Pydantic
-   **Logging:** Python's built-in logging module
-   **Environment Management:** Python-dotenv

### Infrastructure (Required)
-   **Database:** MySQL Server
-   **Google Cloud:** Vertex AI API access with appropriate credentials

## System Workflow (Manual Trigger & Verification Page)

```mermaid
graph TD
    subgraph Single Job Verification (Job Detail Page)
        A1[User clicks "Verify Job" on Job Detail] --> B1[Frontend POST /api/verifications];
        B1 --> C1[Backend creates VerificationRequest];
        C1 --> D1[Backend processes verification asynchronously];
        D1 --> E1[Backend performs verification (BC fetch, document processing)];
        E1 --> F1[Backend calls Python AI Service for document verification];
        F1 --> G1[Backend updates VerificationRequest & Job status];
    end

    subgraph Batch Verification (Job Verification Page)
        A2[User enters Job Number] --> B2{Backend GET /api/verifications/check-eligibility/{jobNo}};
        B2 -- Eligible --> C2[Display 'Add to Verification List' button];
        B2 -- Not Eligible --> D2[Display 'Not Eligible' message];
        C2 --> E2{User clicks 'Add to List'};
        E2 --> F2[Job added to Verification List (Frontend State)];
        G2[User views Verification List] --> H2{User clicks 'Verify All'};
        H2 --> I2[Frontend iterates list, POST /api/verifications for each];
        I2 --> C1;  // Re-uses the single verification backend flow
        D2 --> J2[End];
        F2 --> J2;
    end

    subgraph AI Document Processing
        F1 --> AI1[Extract document identifiers with Gemini];
        AI1 --> AI2[Fetch Business Central data using identifiers];
        AI2 --> AI3[Verify document content against BC data with Gemini];
        AI3 --> G1;
    end

    subgraph Monitoring
        M1[Frontend fetches latest status via GET /api/verifications/job/{jobNo}/latest]
        M2[Frontend fetches dashboard data via GET /api/dashboard/*]
    end
```

**Detailed Flow (Single Trigger):**

1.  **Frontend:** User clicks "Verify Job" on a job detail page.
2.  **Frontend -> Backend:** `POST /api/verifications` request sent with `jobNo`.
3.  **Backend (`VerificationController`):**
    *   Finds/Creates `Job` entity in DB (status `PENDING`).
    *   Creates `VerificationRequest` entity (status `PENDING`, generates UUID). Saves both.
    *   *After DB commit*, triggers asynchronous processing via `VerificationProcessingService`.
    *   Returns `202 Accepted` with `verificationRequestId` to frontend.
4.  **Backend (`VerificationProcessingService`):**
    *   Processes the verification request asynchronously using Spring's `@Async`.
    *   Finds `VerificationRequest` and `Job`. Updates statuses to `PROCESSING`.
    *   Calls `BusinessCentralService` to fetch Job Ledger Entry via OData API.
    *   Calls `JobDocumentVerificationService` to:
        *   Read required documents (`Job Consumption`, `ProformaInvoice`, `Sales Quote`) from `src/Jobs 2nd check pdf/{jobNo}/`.
        *   Convert documents to images using PDFBox.
        *   Call Python AI Service to extract document identifiers using Gemini.
        *   Fetch Business Central data using the extracted identifiers.
        *   Call Python AI Service to verify document content against Business Central data using Gemini.
    *   Updates `VerificationRequest` status (`COMPLETED`/`FAILED`), timestamp, and discrepancy list (as JSON).
    *   Updates `Job` status (`VERIFIED`/`FLAGGED`/`ERROR`).
    *   Saves entities. Logs activity via `ActivityLogService`.
5.  **Frontend (Monitoring):**
    *   `JobDetail` page fetches latest results via `GET /api/verifications/job/{jobNo}/latest`.
    *   Dashboard components fetch data via `/api/dashboard/*` and `/api/activity-log`.

**Detailed Flow (Verification Page):**

1.  **Frontend:** User navigates to the "Job Verification" page.
2.  **Frontend:** User enters a `jobNo` and clicks "Check Eligibility".
3.  **Frontend -> Backend:** `GET /api/verifications/check-eligibility/{jobNo}` request sent.
4.  **Backend (`VerificationController`):**
    *   Calls `BusinessCentralService` to fetch the job entry.
    *   Checks if `firstCheckDate` is present and `secondCheckDate` is absent.
    *   Returns `200 OK` with `{ "isEligible": boolean, "message": "...", ... }`.
5.  **Frontend:** Displays eligibility status and message. If eligible, shows "Add to Verification List" button.
6.  **Frontend:** User clicks "Add to Verification List". Job details are added to a local state array.
7.  **Frontend:** User clicks "Verify All" on the list.
8.  **Frontend:** Iterates through the local list, sending a `POST /api/verifications` request for each `jobNo`.
9.  **Backend:** Each `POST` request follows the "Single Trigger" flow from step 3 onwards.

## Getting Started

### Prerequisites

Make sure you have the following installed on your system:

-   **Node.js:** LTS version recommended (e.g., 18+). Download from [nodejs.org](https://nodejs.org/).
-   **pnpm:** A fast, disk space efficient package manager. Install via Node.js: `npm install -g pnpm`.
-   **Java JDK:** Version 17 or later. Download from [Oracle](https://www.oracle.com/java/technologies/downloads/), [OpenJDK](https://openjdk.java.net/), or use a package manager.
-   **Maven:** Version 3.8+ (often bundled with IDEs, or download from [maven.apache.org](https://maven.apache.org/download.cgi)).
-   **Python:** Version 3.10+. Download from [python.org](https://www.python.org/downloads/).
-   **MySQL Server:** Version 8.0+. Download from [mysql.com](https://dev.mysql.com/downloads/mysql/) or use Docker/package manager.
-   **Git:** Download from [git-scm.com](https://git-scm.com/downloads).
-   **Google Cloud Account:** With Vertex AI API enabled and appropriate credentials.

### Installation & Setup

1.  **Clone the Repository:**
    ```bash
    git clone https://github.com/emagero0/AI-powered-ERP-solution.git # Replace with your repo URL if different
    cd AI-powered-ERP-solution
    ```

2.  **Configure Java Backend Services:**
    *   **Database (MySQL):**
        *   Ensure your MySQL server is running.
        *   Create a database for the application (e.g., `aierpdb`).
        *   Open `src/backend/src/main/resources/application.properties`.
        *   Update `spring.datasource.url` (if your DB name or host/port differs).
        *   Update `spring.datasource.username` and `spring.datasource.password` with your MySQL credentials.
        *   *Note: `spring.jpa.hibernate.ddl-auto=update` will attempt to create/update tables automatically on startup.*
    *   **Business Central API:**
        *   Update the `dynamics.bc.odata.base-url`, `dynamics.bc.odata.username`, and `dynamics.bc.odata.key` properties in `application.properties` with your BC environment details.
    *   **LLM Service Configuration:**
        *   Update the `llm.python.service.baseurl` property in `application.properties` to point to your Python service (default: `http://localhost:8001` for local development).

3.  **Configure Python AI Service:**
    *   Navigate to the Python service directory:
        ```bash
        cd src/gemini-python-service
        ```
    *   Create a virtual environment:
        ```bash
        python -m venv venv
        ```
    *   Activate the virtual environment:
        ```bash
        # On Windows
        venv\Scripts\activate
        # On macOS/Linux
        source venv/bin/activate
        ```
    *   Install dependencies:
        ```bash
        pip install -r requirements.txt
        ```
    *   Create a `.env` file in the `src/gemini-python-service` directory with your Google Cloud credentials:
        ```
        GCP_PROJECT_ID=your-project-id
        GCP_LOCATION=us-central1
        GEMINI_MODEL_NAME=gemini-2.0-flash-001
        # Optional: Path to service account key file if not using application default credentials
        # GOOGLE_APPLICATION_CREDENTIALS=path/to/your/service-account-key.json
        ```

4.  **Prepare Test Data (Optional):**
    *   Place the necessary PDF documents for testing (e.g., for job J069023) in the correct directory structure: `src/Jobs 2nd check pdf/J069023/Job Consumption.pdf`, `src/Jobs 2nd check pdf/J069023/ProformaInvoice.pdf`, etc.

5.  **Start the Python AI Service:**
    *   With the virtual environment activated, run:
        ```bash
        python -m app.main
        ```
    *   The Python service should now be running at `http://localhost:8001`.

6.  **Build and Run the Java Backend:**
    *   Open a **new terminal** and navigate to the backend directory:
        ```bash
        cd src/backend
        ```
    *   Build the project using Maven (this also downloads dependencies):
        ```bash
        mvn clean install -DskipTests # Use -DskipTests if you want to skip running tests during build
        ```
    *   Run the application:
        ```bash
        mvn spring-boot:run
        ```
    *   The backend API should now be running, typically at `http://localhost:8081`. Check the console output for the exact port and status.

7.  **Install and Run the Frontend (React/Vite):**
    *   Open a **new terminal** and navigate to the frontend directory:
        ```bash
        cd src/frontend
        ```
    *   Install dependencies using pnpm:
        ```bash
        pnpm install
        ```
    *   Start the development server:
        ```bash
        pnpm run dev
        ```
    *   The frontend application should now be running, typically at `http://localhost:5173`. Open this URL in your web browser.

8.  **Docker Deployment (Optional):**
    *   The project includes Docker configuration for containerized deployment.
    *   Update the `docker-compose.yml` file as needed for your environment.
    *   Run the following command to start all services:
        ```bash
        docker-compose up -d
        ```

## API Endpoints

*   `POST /api/verifications`: Trigger a new verification request.
    *   Body: `{ "jobNo": "string" }`
    *   Response: `202 Accepted`, Body: `{ "verificationRequestId": "string" }`
*   `GET /api/verifications/check-eligibility/{jobNo}`: Check if a job is eligible for verification (1st check done, 2nd check pending).
    *   Response: `200 OK`, Body: `EligibilityCheckResponseDTO`
*   `GET /api/verifications/{id}`: Get details of a specific verification request by its UUID.
*   `GET /api/verifications/job/{jobNo}/latest`: Get the latest verification request details for a specific Job Number.
*   `GET /api/jobs`: Get a summary list of all jobs.
*   `GET /api/jobs/{id}`: Get detailed information for a job by its internal database ID.
*   `GET /api/dashboard/stats`: Get overall dashboard statistics.
*   `GET /api/dashboard/daily-stats`: Get aggregated daily verification counts for the last 7 days.
*   `GET /api/activity-log`: Get paginated recent activity logs.
*   `POST /api/feedback`: Submit user feedback for a job.

## Contributing

Please read [CONTRIBUTING.md](CONTRIBUTING.md) for details on our code of conduct and the process for submitting pull requests.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
