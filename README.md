# AI-powered ERP Solution

A comprehensive Enterprise Resource Planning (ERP) solution powered by artificial intelligence to streamline business operations and enhance decision-making processes.

## Features

- AI-driven analytics and insights
- Business process automation
- Integrated modules for various business functions
- Real-time reporting and dashboards
- Smart resource allocation
- Predictive analytics

## Technology Stack

### Frontend
- React.js with Vite (TypeScript)
- Styling: TailwindCSS
- Component Library: shadcn/ui
- Icons: TBD (choosing between Lucide UI and Iconify)

### Backend
- Spring Boot (Java) using Maven
- Database: MySQL
- Authentication: OAuth2.0
- Database Migrations: Flyway
- API Documentation: Swagger/Springdoc OpenAPI
- Testing: Unit tests with JUnit & Mockito, plus integration tests
- Logging: Logback and Spring Boot Actuator (with optional Prometheus/Grafana integration)
- Exception Handling: Global error handling and enhanced security (including CSRF protection and rate limiting)
- Configuration: Externalized via Spring Boot properties and environment variables (optionally Spring Cloud Config)
- Containerization & CI/CD: Docker and Kubernetes; CI/CD pipelines using GitHub Actions or Jenkins

### AI/ML
- TensorFlow/PyTorch

## Getting Started

### Prerequisites

- Node.js 18+
- Java JDK 17 or later
- Maven 3.8+
- MySQL 8.0+
- Git
- Docker (optional for containerization)
- Kubernetes (optional for orchestration)

### Installation

1. Clone the repository:
```bash
git clone https://github.com/emagero0/AI-powered-ERP-solution.git
cd AI-powered-ERP-solution
```

2. Set up backend:
```bash
# Navigate to backend directory
cd src/backend

# Build with Maven
mvn clean install

# Run the application
mvn spring-boot:run
```

3. Set up frontend:
```bash
# Navigate to frontend directory
cd src/frontend

# Install dependencies
npm create vite@latest . -- --template react-ts
npm install tailwindcss postcss autoprefixer
npm install @shadcn/ui
npm install -D @types/node

# Initialize Tailwind CSS
npx tailwindcss init -p

# Start development server
npm run dev
```


## Contributing

Please read [CONTRIBUTING.md](CONTRIBUTING.md) for details on our code of conduct and the process for submitting pull requests.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.