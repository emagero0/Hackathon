import { Card, CardContent, CardHeader, CardTitle } from "../../components/ui/card"
import { Badge } from "../../components/ui/badge"
import { Alert, AlertTitle, AlertDescription } from "../../components/ui/alert"
import { AlertTriangle, CheckCircle, Clock, CheckCircle2 } from "lucide-react"
import { format } from 'date-fns' // Added import

// Re-define interface for JobDetailData (matching parent component)
// Ideally, this should be imported from a shared types file
// interface DiscrepancyDTO {
//   discrepancyType: string;
//   fieldName: string | null;
//   expectedValue: string | null;
//   actualValue: string | null;
//   description: string;
// }

// interface VerificationDetailsDTO {
//   verificationTimestamp: string;
//   aiConfidenceScore: number | null;
//   rawAiResponse: string | null;
//   discrepancies: DiscrepancyDTO[];
// }

interface JobDetailData {
  internalId: number;
  businessCentralJobId: string;
  jobTitle: string;
  customerName: string;
  status: 'PENDING' | 'PROCESSING' | 'VERIFIED' | 'FLAGGED' | 'ERROR';
  lastProcessedAt: string | null; // Allow null
  verificationResult: string | null; // Verification result message
  hasDiscrepancies: boolean | null; // Whether the verification found discrepancies
}

interface JobDetailsProps {
  jobData: JobDetailData | null; // Accept the full job data object
}

export function JobDetails({ jobData }: JobDetailsProps) {

  // Handle null jobData case (e.g., during initial load or if not found)
  if (!jobData) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>Job Details</CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-muted-foreground">Job data not available.</p>
        </CardContent>
      </Card>
    );
  }

  const getStatusBadge = (status: JobDetailData['status']) => { // Use JobDetailData status type
    switch (status) {
      case "VERIFIED": // Match backend enum
        return (
          <Badge
            variant="outline"
            className="bg-green-50 text-green-700 border-green-200 dark:bg-green-950 dark:text-green-400 dark:border-green-800"
          >
            <CheckCircle className="mr-1 h-3 w-3" />
            Verified
          </Badge>
        )
      case "FLAGGED": // Match backend enum
        return (
          <Badge
            variant="outline"
            className="bg-red-50 text-red-700 border-red-200 dark:bg-red-950 dark:text-red-400 dark:border-red-800"
          >
            <AlertTriangle className="mr-1 h-3 w-3" />
            Flagged
          </Badge>
        )
      case "PENDING": // Match backend enum
      case "PROCESSING": // Group PROCESSING with PENDING visually
        return (
          <Badge
            variant="outline"
            className="bg-amber-50 text-amber-700 border-amber-200 dark:bg-amber-950 dark:text-amber-400 dark:border-amber-800"
          >
            <Clock className="mr-1 h-3 w-3" />
            Pending
          </Badge>
        )
      case "ERROR": // Added Error status
        return (
          <Badge
            variant="outline"
            className="bg-gray-100 text-gray-700 border-gray-200 dark:bg-gray-800 dark:text-gray-400 dark:border-gray-700"
          >
            <AlertTriangle className="mr-1 h-3 w-3 text-destructive" />
            Error
          </Badge>
        )
      default:
        // Fallback for unexpected status values
        return <Badge variant="secondary">{status}</Badge>
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center justify-between">
          <span>Job Details</span>
          {getStatusBadge(jobData.status)}
        </CardTitle>
      </CardHeader>
      <CardContent>
        <div className="space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <div>
              <div className="text-sm font-medium text-muted-foreground">BC Job ID</div>
              <div>{jobData.businessCentralJobId || ''}</div>
            </div>
             <div>
              <div className="text-sm font-medium text-muted-foreground">Internal ID</div>
              <div>{jobData.internalId}</div>
            </div>
            <div>
              <div className="text-sm font-medium text-muted-foreground">Title</div>
              <div>{jobData.jobTitle || ''}</div>
            </div>
            <div>
              <div className="text-sm font-medium text-muted-foreground">Customer</div>
              <div>{jobData.customerName || ''}</div>
            </div>
             <div>
              <div className="text-sm font-medium text-muted-foreground">Last Processed</div>
              <div>
                {jobData.lastProcessedAt
                  ? format(new Date(jobData.lastProcessedAt), 'PPpp')
                  : ''}
              </div>
            </div>
             {/* Remove fields not present in JobDetailData */}
            {/*
            <div>
              <div className="text-sm font-medium text-muted-foreground">Amount</div>
              <div>{job.amount}</div>
            </div>
            <div>
              <div className="text-sm font-medium text-muted-foreground">Reference</div>
              <div>{job.reference}</div>
            </div>
            <div>
              <div className="text-sm font-medium text-muted-foreground">Department</div>
              <div>{job.department}</div>
            </div>
            <div>
              <div className="text-sm font-medium text-muted-foreground">Assigned To</div>
              <div>{job.assignedTo}</div>
            </div>
            <div>
              <div className="text-sm font-medium text-muted-foreground">Created</div>
              <div>{job.createdAt}</div>
            </div>
            */}
          </div>

          {/* Verification Result Section */}
          {(jobData.status === 'VERIFIED' || jobData.status === 'FLAGGED') && (
            <div className="mt-4 pt-4 border-t">
              <h3 className="text-sm font-medium mb-2">Verification Result</h3>

              {jobData.hasDiscrepancies ? (
                <Alert variant="destructive" className="mb-2">
                  <AlertTriangle className="h-4 w-4" />
                  <AlertTitle>Verification Flagged</AlertTitle>
                  <AlertDescription>
                    {jobData.verificationResult || "Verification completed with discrepancies"}
                  </AlertDescription>
                </Alert>
              ) : (
                <Alert variant="default" className="mb-2">
                  <CheckCircle2 className="h-4 w-4" />
                  <AlertTitle>Verification Successful</AlertTitle>
                  <AlertDescription>
                    {jobData.verificationResult || "All document checks passed successfully"}
                  </AlertDescription>
                </Alert>
              )}
            </div>
          )}
        </div>
      </CardContent>
    </Card>
  )
}
