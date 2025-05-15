"use client"

import { Card, CardContent, CardHeader, CardTitle } from "../../components/ui/card"
import { Button } from "../../components/ui/button"
import { Textarea } from "../../components/ui/textarea"
import { RadioGroup, RadioGroupItem } from "../../components/ui/radio-group"
import { Label } from "../../components/ui/label"
import { useState } from "react"
import { CheckCircle, ThumbsDown, ThumbsUp, Loader2 } from "lucide-react" // Added Loader2
import { submitFeedback } from "../../lib/api" // Added import
// import { useToast } from "../../components/ui/use-toast" // Removed toast import for now

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
  lastProcessedAt: string | null; // Allow null to match parent potentially
  // verificationDetails: VerificationDetailsDTO | null; // Removed - This component doesn't use verification details
}


interface JobFeedbackProps {
   jobData: JobDetailData | null; // Accept the full job data object
}

export function JobFeedback({ jobData }: JobFeedbackProps) {
  const [feedbackType, setFeedbackType] = useState<string>("accurate")
  const [comments, setComments] = useState<string>("")
  const [submitted, setSubmitted] = useState<boolean>(false)
  const [isSubmitting, setIsSubmitting] = useState<boolean>(false)
  const [error, setError] = useState<string | null>(null)
  // const { toast } = useToast() // Removed toast initialization

  const handleSubmit = async () => {
    if (!jobData) {
      setError("Job data is not available to submit feedback.");
      return;
    }

    setIsSubmitting(true);
    setError(null);

    const feedbackPayload = {
      jobId: jobData.internalId, // Use internal ID
      isCorrect: feedbackType === "accurate",
      feedbackText: comments,
      userIdentifier: "frontend-user" // Placeholder - replace with actual user ID/name later
    };

    try {
      await submitFeedback(feedbackPayload);
      setSubmitted(true);
      // Optionally clear comments after successful submission
      // setComments("");
      // toast({ // Removed toast call
      //   title: "Feedback Submitted",
      //   description: "Thank you for your input!",
      // });
      console.log("Feedback submitted successfully."); // Log success instead
    } catch (err) {
      console.error("Failed to submit feedback:", err);
      setError("Failed to submit feedback. Please try again.");
      // toast({ // Removed toast call
      //   title: "Submission Failed",
      //   description: "Could not submit feedback. Please try again later.",
      //   variant: "destructive",
      // });
    } finally {
      setIsSubmitting(false);
    }
  }

  // Handle null jobData case
  if (!jobData) {
     return (
      <Card>
        <CardHeader>
          <CardTitle>Provide Feedback</CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-muted-foreground">Job data not available.</p>
        </CardContent>
      </Card>
    );
  }

  if (submitted) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>Feedback</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="flex flex-col items-center justify-center py-6 text-center">
            <div className="rounded-full bg-green-100 p-3 dark:bg-green-900">
              <CheckCircle className="h-6 w-6 text-green-600 dark:text-green-400" />
            </div>
            <h3 className="mt-4 text-lg font-medium">Feedback Submitted!</h3>
            <p className="mt-2 text-sm text-muted-foreground">
              Thank you! Your input helps us improve the AI verification system.
            </p>
            {/* Keep button to allow resubmission or hide */}
            {/* <Button className="mt-4" variant="outline" onClick={() => setSubmitted(false)}>
              Submit Again
            </Button> */}
          </div>
        </CardContent>
      </Card>
    )
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Provide Feedback</CardTitle>
      </CardHeader>
      <CardContent>
        <div className="space-y-6">
          <div>
            <div className="text-sm font-medium mb-3">Was the AI verification accurate?</div>
            <RadioGroup value={feedbackType} onValueChange={setFeedbackType} className="flex flex-col space-y-3">
              <div className="flex items-center space-x-2">
                <RadioGroupItem value="accurate" id="accurate" />
                <Label htmlFor="accurate" className="flex items-center">
                  <ThumbsUp className="mr-2 h-4 w-4 text-green-500" />
                  Yes, the verification was accurate
                </Label>
              </div>
              <div className="flex items-center space-x-2">
                <RadioGroupItem value="inaccurate" id="inaccurate" />
                <Label htmlFor="inaccurate" className="flex items-center">
                  <ThumbsDown className="mr-2 h-4 w-4 text-red-500" />
                  No, there were issues with the verification
                </Label>
              </div>
            </RadioGroup>
          </div>

          <div>
            <div className="text-sm font-medium mb-3">Additional Comments</div>
            <Textarea
              placeholder="Please provide any additional feedback or details about the verification..."
              value={comments}
              onChange={(e) => setComments(e.target.value)}
              rows={4}
            />
          </div>

          {error && <p className="text-sm text-red-500 mt-2">{error}</p>}
          <Button onClick={handleSubmit} disabled={isSubmitting}>
            {isSubmitting ? (
              <>
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                Submitting...
              </>
            ) : (
              "Submit Feedback"
            )}
          </Button>
        </div>
      </CardContent>
    </Card>
  )
}
