"use client"

import { useParams, Link } from "react-router-dom"
import { JobDetails } from "../components/jobs/job-details"
import { JobDocuments } from "../components/jobs/job-documents"
import { JobVerificationResults } from "../components/jobs/job-verification-results"
import { JobFeedback } from "../components/jobs/job-feedback"
import { Button } from "../components/ui/button"
import { ArrowLeft, Loader2, AlertTriangle } from "lucide-react" // Added Loader2, AlertTriangle
import { useState, useEffect } from "react" // Added imports
import { getJob } from "../lib/api" // Added import

// Define interface for JobDetailDTO (matching backend)
// This might be better placed in a shared types file
interface DiscrepancyDTO {
  discrepancyType: string;
  fieldName: string | null;
  expectedValue: string | null;
  actualValue: string | null;
  description: string;
}

interface VerificationDetailsDTO {
  verificationTimestamp: string;
  aiConfidenceScore: number | null;
  rawAiResponse: string | null;
  discrepancies: DiscrepancyDTO[];
}

interface JobDetailData {
  internalId: number;
  businessCentralJobId: string;
  jobTitle: string;
  customerName: string;
  status: 'PENDING' | 'PROCESSING' | 'VERIFIED' | 'FLAGGED' | 'ERROR';
  lastProcessedAt: string;
  verificationDetails: VerificationDetailsDTO | null;
}


export default function JobDetail() {
  const { id } = useParams<{ id: string }>()
  const [jobData, setJobData] = useState<JobDetailData | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!id) {
      setError("Job ID is missing.");
      setIsLoading(false);
      return;
    }

    const fetchJobDetail = async () => {
      setIsLoading(true);
      setError(null);
      try {
        // Ensure id is treated as string for API call if needed, though backend expects Long
        const data = await getJob(id);
        setJobData(data);
      } catch (err) {
        setError("Failed to fetch job details.");
        console.error(err);
      } finally {
        setIsLoading(false);
      }
    };

    fetchJobDetail();
  }, [id]); // Re-fetch if id changes

  if (isLoading) {
    return (
      <main className="flex min-h-screen flex-col items-center justify-center p-6">
        <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
        <p className="mt-2 text-muted-foreground">Loading job details...</p>
      </main>
    );
  }

  if (error) {
     return (
      <main className="flex min-h-screen flex-col items-center justify-center p-6">
        <AlertTriangle className="h-8 w-8 text-red-500" />
        <p className="mt-2 text-red-500">{error}</p>
         <Button variant="outline" size="sm" asChild className="mt-4">
          <Link to="/jobs">
            <ArrowLeft className="mr-2 h-4 w-4" />
            Back to jobs
          </Link>
        </Button>
      </main>
    );
  }

   if (!jobData) {
     return (
      <main className="flex min-h-screen flex-col items-center justify-center p-6">
        <p className="mt-2 text-muted-foreground">Job not found.</p>
         <Button variant="outline" size="sm" asChild className="mt-4">
          <Link to="/jobs">
            <ArrowLeft className="mr-2 h-4 w-4" />
            Back to jobs
          </Link>
        </Button>
      </main>
    );
  }

  // Display Business Central Job ID in the header
  const displayJobId = jobData.businessCentralJobId || `Internal ID: ${id}`;

  return (
    <main className="flex min-h-screen flex-col p-6">
      <div className="flex items-center gap-4 mb-6">
        <Button variant="outline" size="icon" asChild>
          <Link to="/jobs">
            <ArrowLeft className="h-4 w-4" />
            <span className="sr-only">Back to jobs</span>
          </Link>
        </Button>
        <h1 className="text-2xl font-bold">Job {displayJobId}</h1>
      </div>

      <div className="grid gap-6 md:grid-cols-2">
        <div>
          {/* Pass fetched jobData to child components */}
          <JobDetails jobData={jobData} />
        </div>
        <div>
          {/* Keep passing ID for now, might change later */}
          <JobDocuments jobId={id || ""} />
        </div>
      </div>

      <div className="mt-6">
         {/* Pass fetched jobData to child components */}
        <JobVerificationResults jobData={jobData} />
      </div>

      <div className="mt-6">
         {/* Pass fetched jobData to child components */}
        <JobFeedback jobData={jobData} />
      </div>
    </main>
  )
}
