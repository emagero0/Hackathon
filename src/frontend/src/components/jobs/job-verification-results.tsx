import { Card, CardContent, CardHeader, CardTitle } from "../../components/ui/card"
import { Badge } from "../../components/ui/badge"
import { AlertTriangle, CheckCircle, Clock } from "lucide-react"
import { Progress } from "../../components/ui/progress"
import { format } from 'date-fns' // Added import

// Re-define interface for JobDetailData (matching parent component)
// Ideally, this should be imported from a shared types file
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


interface JobVerificationResultsProps {
   jobData: JobDetailData | null; // Accept the full job data object
}

export function JobVerificationResults({ jobData }: JobVerificationResultsProps) {

  // Handle null jobData or missing verificationDetails
  if (!jobData || !jobData.verificationDetails) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>Verification Results</CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-muted-foreground">Verification results not available.</p>
        </CardContent>
      </Card>
    );
  }

  const results = jobData.verificationDetails; // Use data from prop

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
          <span>Verification Results</span>
          {/* Use the overall job status */}
          {getStatusBadge(jobData.status)}
        </CardTitle>
      </CardHeader>
      <CardContent>
        <div className="space-y-6">
          <div className="grid grid-cols-2 sm:grid-cols-3 gap-4"> {/* Adjusted grid for smaller screens */}
            <div>
              <div className="text-sm font-medium text-muted-foreground">Verification Time</div>
              <div>
                {results.verificationTimestamp
                  ? format(new Date(results.verificationTimestamp), 'PPpp')
                  : 'N/A'}
              </div>
            </div>
            {/* Duration is not available from backend DTO */}
            {/*
            <div>
              <div className="text-sm font-medium text-muted-foreground">Duration</div>
              <div>{results.duration}</div>
            </div>
            */}
            <div>
              <div className="text-sm font-medium text-muted-foreground">AI Confidence</div>
              {results.aiConfidenceScore !== null ? (
                <div className="flex items-center gap-2">
                  <span>{(results.aiConfidenceScore * 100).toFixed(0)}%</span>
                  <Progress value={results.aiConfidenceScore * 100} className="h-2 w-20" />
                </div>
              ) : (
                <div>N/A</div>
              )}
            </div>
          </div>

          {/* Display Discrepancies */}
          {results.discrepancies && results.discrepancies.length > 0 && (
            <div className="rounded-lg border">
              <div className="bg-muted px-4 py-2 rounded-t-lg font-medium text-red-600 dark:text-red-400">
                Detected Discrepancies
              </div>
              <div className="divide-y">
                {results.discrepancies.map((discrepancy, i) => (
                  <div key={i} className="px-4 py-3 space-y-1">
                    <div className="flex items-center gap-2 font-medium">
                       <AlertTriangle className="h-4 w-4 text-red-500 flex-shrink-0" />
                       <span>{discrepancy.discrepancyType || 'Unknown Issue'}</span>
                    </div>
                    <p className="text-sm text-muted-foreground pl-6">
                      {discrepancy.description || 'No details provided.'}
                    </p>
                    {/* Optionally display field, expected, actual if available */}
                    {discrepancy.fieldName && (
                       <p className="text-xs text-muted-foreground pl-6">Field: {discrepancy.fieldName}</p>
                    )}
                     {discrepancy.expectedValue && (
                       <p className="text-xs text-muted-foreground pl-6">Expected: {discrepancy.expectedValue}</p>
                    )}
                     {discrepancy.actualValue && (
                       <p className="text-xs text-muted-foreground pl-6">Actual: {discrepancy.actualValue}</p>
                    )}
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Display Raw AI Response */}
          {results.rawAiResponse && (
            <div className="rounded-lg border p-4 bg-muted/50 dark:bg-muted/20">
               <div className="font-medium mb-2">AI Analysis Notes</div>
               <p className="text-sm whitespace-pre-wrap font-mono text-muted-foreground">
                 {results.rawAiResponse}
               </p>
            </div>
          )}
        </div>
      </CardContent>
    </Card>
  )
}
