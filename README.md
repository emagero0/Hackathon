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
- Python (FastAPI)
- Database: PostgreSQL
- Authentication: JWT

### AI/ML
- TensorFlow/PyTorch

## Getting Started

### Prerequisites

- Node.js 18+
- Python 3.9+
- PostgreSQL
- Git

### Installation

1. Clone the repository:
```bash
git clone https://github.com/yourusername/AI-powered-ERP-solution.git
cd AI-powered-ERP-solution
```

2. Set up frontend:
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

3. Set up backend:
```bash
cd src/backend
python -m venv venv
source venv/bin/activate  # On Windows: venv\Scripts\activate
pip install -r requirements.txt
python main.py
```

## Contributing

Please read [CONTRIBUTING.md](CONTRIBUTING.md) for details on our code of conduct and the process for submitting pull requests.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
