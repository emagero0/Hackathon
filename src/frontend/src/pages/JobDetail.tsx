"use client"

import { useParams, Link } from "react-router-dom";
import { JobDetails } from "../components/jobs/job-details";
import { JobDocuments } from "../components/jobs/job-documents";
// Removed import for JobVerificationResults
import { JobFeedback } from "../components/jobs/job-feedback";
import { Button } from "../components/ui/button";
import { ArrowLeft, Loader2, AlertTriangle, CheckCircle2, XCircle, FileCheck, FileWarning, FileX, Hourglass } from "lucide-react";
import { useState, useEffect } from "react";
import { getJob, getLatestVerificationForJob } from "../lib/api"; // Import correct API functions
import { Card, CardContent, CardHeader, CardTitle } from "../components/ui/card";
import { Badge } from "../components/ui/badge";
import { Alert, AlertDescription, AlertTitle } from "../components/ui/alert";
import { format, parseISO } from 'date-fns';
import { Skeleton } from "../components/ui/skeleton";

// --- Interfaces --- (Should ideally be in a shared types file)

// Represents the basic Job data fetched by getJob
// NOTE: Removed verificationDetails from here, as it's fetched separately now.
interface JobDetailData {
  internalId: number;
  businessCentralJobId: string;
  jobTitle: string;
  customerName: string;
  status: 'PENDING' | 'PROCESSING' | 'VERIFIED' | 'FLAGGED' | 'ERROR'; // Overall Job status
  lastProcessedAt: string | null; // Allow null
}

// Represents the data for the latest verification attempt
interface LatestVerificationResponse {
  verificationRequestId: string;
  jobNo: string;
  status: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED'; // Verification attempt status
  requestTimestamp: string;
  resultTimestamp: string | null;
  discrepancies: string[];
}

// --- Helper Functions ---

// Get badge variant based on VerificationRequest.VerificationStatus
const getVerificationStatusVariant = (status: LatestVerificationResponse['status']): "default" | "destructive" | "secondary" | "outline" => {
  switch (status) {
    case 'COMPLETED': // Treat COMPLETED as default/success visually here
      return 'default';
    case 'FAILED':
      return 'destructive';
    case 'PROCESSING':
      return 'outline';
    case 'PENDING':
    default:
      return 'secondary';
  }
};

// Get icon based on VerificationRequest.VerificationStatus
const getVerificationStatusIcon = (status: LatestVerificationResponse['status']) => {
   switch (status) {
    case 'COMPLETED':
      return <FileCheck className="h-4 w-4 mr-1" />; // Use FileCheck for completed
    case 'FAILED':
      return <FileX className="h-4 w-4 mr-1" />;
    case 'PROCESSING':
       return <Loader2 className="h-4 w-4 mr-1 animate-spin" />;
    case 'PENDING':
    default:
      return <Hourglass className="h-4 w-4 mr-1" />;
  }
}

// --- Component ---

export default function JobDetail() {
  const { id } = useParams<{ id: string }>(); // Internal DB ID for the Job
  const [jobData, setJobData] = useState<JobDetailData | null>(null);
  const [latestVerification, setLatestVerification] = useState<LatestVerificationResponse | null>(null);
  const [isLoadingJob, setIsLoadingJob] = useState(true);
  const [isLoadingVerification, setIsLoadingVerification] = useState(true);
  const [jobError, setJobError] = useState<string | null>(null);
  const [verificationError, setVerificationError] = useState<string | null>(null);

  useEffect(() => {
    let isMounted = true;

    const fetchJobAndVerification = async () => {
      if (!id) {
        setJobError("Job ID is missing.");
        setIsLoadingJob(false);
        setIsLoadingVerification(false);
        return;
      }

      setIsLoadingJob(true);
      setIsLoadingVerification(true);
      setJobError(null);
      setVerificationError(null);
      setJobData(null); // Reset previous data
      setLatestVerification(null); // Reset previous data


      try {
        // Fetch basic job data using internal ID
        const jobDetailData = await getJob(id); // Fetches data matching backend JobDetailDTO
        if (!isMounted) return;

        // Prepare data for JobDetailData state (handle null, remove verificationDetails)
        const processedJobData: JobDetailData = {
            internalId: jobDetailData.internalId,
            businessCentralJobId: jobDetailData.businessCentralJobId,
            jobTitle: jobDetailData.jobTitle,
            customerName: jobDetailData.customerName,
            status: jobDetailData.status,
            lastProcessedAt: jobDetailData.lastProcessedAt || '', // Provide default empty string
        };
        setJobData(processedJobData);

        // If job data fetched successfully, fetch latest verification using jobNo
        if (jobDetailData?.businessCentralJobId) {
          try {
            const verificationData = await getLatestVerificationForJob(jobDetailData.businessCentralJobId);
             if (!isMounted) return;
            setLatestVerification(verificationData);
          } catch (verifErr) {
             if (!isMounted) return;
            // Don't set a blocking error, just log it or show a subtle warning
            setVerificationError("Could not load latest verification details.");
            console.error("Verification fetch error:", verifErr);
          }
        } else {
           if (!isMounted) return;
           setVerificationError("Business Central Job Number not found in job details.");
        }

      } catch (jobErr) {
         if (!isMounted) return;
        setJobError("Failed to fetch job details.");
        console.error("Job fetch error:", jobErr);
      } finally {
         if (!isMounted) return;
        setIsLoadingJob(false);
        setIsLoadingVerification(false);
      }
    };

    fetchJobAndVerification();

    return () => {
      isMounted = false;
    };
  }, [id]);

  // --- Render Logic ---

  if (isLoadingJob) { // Show loading skeleton only while fetching the main job data
    return (
      <main className="flex min-h-screen flex-col p-6">
         <div className="flex items-center gap-4 mb-6">
            <Skeleton className="h-10 w-10 rounded-md" />
            <Skeleton className="h-8 w-48" />
         </div>
         <div className="grid gap-6 md:grid-cols-2">
           <Skeleton className="h-48 w-full rounded-lg" />
           <Skeleton className="h-48 w-full rounded-lg" />
         </div>
         <div className="mt-6">
           <Skeleton className="h-64 w-full rounded-lg" />
         </div>
         <div className="mt-6">
           <Skeleton className="h-40 w-full rounded-lg" />
         </div>
         <div className="mt-6">
           <Skeleton className="h-40 w-full rounded-lg" /> {/* Skeleton for verification card */}
         </div>
      </main>
    );
  }

  if (jobError) { // Show error if fetching the main job failed
     return (
      <main className="flex min-h-screen flex-col items-center justify-center p-6">
        <AlertTriangle className="h-8 w-8 text-red-500" />
        <p className="mt-2 text-red-500">{jobError}</p>
         <Button variant="outline" size="sm" asChild className="mt-4">
          <Link to="/jobs">
            <ArrowLeft className="mr-2 h-4 w-4" />
            Back to jobs
          </Link>
        </Button>
      </main>
    );
  }

   if (!jobData) { // Show not found if job fetch succeeded but returned no data
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
      {/* Header */}
      <div className="flex items-center gap-4 mb-6">
        <Button variant="outline" size="icon" asChild>
          <Link to="/jobs">
            <ArrowLeft className="h-4 w-4" />
            <span className="sr-only">Back to jobs</span>
          </Link>
        </Button>
        <h1 className="text-2xl font-bold">Job {displayJobId}</h1>
      </div>

      {/* Main Content Grid */}
      <div className="grid gap-6 md:grid-cols-2">
        <div>
          {/* Pass jobData that now matches the expected type */}
          <JobDetails jobData={jobData} />
        </div>
        <div>
          <JobDocuments jobNo={jobData.businessCentralJobId || ""} />
        </div>
      </div>

      {/* Removed Old Verification Results component */}

      {/* Feedback */}
      <div className="mt-6">
         {/* Pass jobData that now matches the expected type */}
        <JobFeedback jobData={jobData} />
      </div>

      {/* New Document Verification Card */}
      <div className="mt-6">
        <Card>
          <CardHeader>
            <CardTitle>Latest Document Verification</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            {isLoadingVerification ? (
               <Skeleton className="h-20 w-full rounded-md" />
            ) : verificationError ? (
               <Alert variant="destructive">
                 <AlertTriangle className="h-4 w-4" />
                 <AlertTitle>Could Not Load Verification</AlertTitle>
                 <AlertDescription>{verificationError}</AlertDescription>
               </Alert>
            ) : latestVerification ? (
              <>
                {/* Status Badge */}
                <div className="flex items-center space-x-2">
                  <span className="font-semibold">Status:</span>
                  <Badge variant={
                      latestVerification.status === 'COMPLETED' && latestVerification.discrepancies.length > 0
                        ? 'destructive' // Use destructive if completed with discrepancies
                        : getVerificationStatusVariant(latestVerification.status)
                  }>
                    {getVerificationStatusIcon(latestVerification.status)}
                    {latestVerification.status}
                  </Badge>
                </div>
                {/* Timestamps */}
                 <div className="text-sm text-muted-foreground">
                   Requested: {format(parseISO(latestVerification.requestTimestamp), "PPP p")}
                 </div>
                {latestVerification.resultTimestamp && (
                  <div className="text-sm text-muted-foreground">
                    Completed: {format(parseISO(latestVerification.resultTimestamp), "PPP p")}
                  </div>
                )}
                 {/* Request ID */}
                 <div className="text-sm text-muted-foreground">
                    Request ID: {latestVerification.verificationRequestId}
                 </div>

                {/* Discrepancies List (only if COMPLETED and discrepancies exist) */}
                {latestVerification.status === 'COMPLETED' && latestVerification.discrepancies.length > 0 && (
                  <Alert variant="destructive">
                    <FileWarning className="h-4 w-4" /> {/* Changed icon */}
                    <AlertTitle>Discrepancies Found</AlertTitle>
                    <AlertDescription>
                      <ul className="list-disc pl-5 mt-2 space-y-1">
                        {latestVerification.discrepancies.map((d, index) => (
                          <li key={index}>{d}</li>
                        ))}
                      </ul>
                    </AlertDescription>
                  </Alert>
                )}

                {/* Failure Alert */}
                {latestVerification.status === 'FAILED' && (
                  <Alert variant="destructive">
                    <XCircle className="h-4 w-4" />
                    <AlertTitle>Verification Failed</AlertTitle>
                    <AlertDescription>
                      {latestVerification.discrepancies[0] || "An error occurred during the verification process."}
                    </AlertDescription>
                  </Alert>
                )}

                {/* Success Alert */}
                 {latestVerification.status === 'COMPLETED' && latestVerification.discrepancies.length === 0 && (
                     <Alert variant="default">
                       <CheckCircle2 className="h-4 w-4" />
                       <AlertTitle>Verification Successful</AlertTitle>
                       <AlertDescription>
                         All document checks passed successfully.
                       </AlertDescription>
                     </Alert>
                 )}

                {/* Pending/Processing Alert */}
                 {(latestVerification.status === 'PENDING' || latestVerification.status === 'PROCESSING') && (
                    <Alert>
                      {getVerificationStatusIcon(latestVerification.status)}
                      <AlertTitle>Verification {latestVerification.status}</AlertTitle>
                      <AlertDescription>
                        The verification process is currently running or queued.
                      </AlertDescription>
                    </Alert>
                 )}
              </>
            ) : (
              <div className="text-sm text-muted-foreground">No verification data available for this job.</div>
            )}
          </CardContent>
        </Card>
      </div>
      {/* End Updated Card */}

    </main>
  )
}
