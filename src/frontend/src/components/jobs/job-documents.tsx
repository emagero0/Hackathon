"use client"

import { useState, useEffect } from 'react';
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Download, Eye, FileText, Image, PaperclipIcon as PaperClip, Loader2, AlertCircle, CheckCircle2, Info } from "lucide-react"
import { checkJobEligibility, triggerVerification } from "@/services/verificationService";

// Define the type for the eligibility check response
interface EligibilityCheckResponse {
  isEligible: boolean;
  jobNo: string;
  jobTitle?: string;
  customerName?: string;
  message: string;
}

interface JobDocumentsProps {
  jobNo: string;
}

// Define the static list of relevant verification documents
const verificationDocuments = [
  { id: "job-consumption", name: "Job Consumption.pdf", type: "pdf" },
  { id: "proforma-invoice", name: "ProformaInvoice.pdf", type: "pdf" },
  { id: "sales-quote", name: "Sales quote.pdf", type: "pdf" },
];

export function JobDocuments({ jobNo }: JobDocumentsProps) {
  console.log("Rendering documents for jobNo:", jobNo);

  // State for eligibility check
  const [eligibilityStatus, setEligibilityStatus] = useState<'idle' | 'loading' | 'success' | 'error'>('idle');
  const [eligibilityData, setEligibilityData] = useState<EligibilityCheckResponse | null>(null);
  const [eligibilityError, setEligibilityError] = useState<string | null>(null);

  // State for triggering verification
  const [isVerifying, setIsVerifying] = useState(false);
  const [verificationTriggered, setVerificationTriggered] = useState(false);
  const [verificationError, setVerificationError] = useState<string | null>(null);

  // Use the static list
  const documents = verificationDocuments;

  // useEffect to check eligibility when jobNo changes
  useEffect(() => {
    if (!jobNo) {
      setEligibilityStatus('idle');
      setEligibilityData(null);
      setEligibilityError(null);
      return;
    }

    const fetchEligibility = async () => {
      setEligibilityStatus('loading');
      setEligibilityData(null);
      setEligibilityError(null);
      setVerificationTriggered(false);
      setVerificationError(null);
      try {
        console.log(`Checking eligibility for job: ${jobNo}`);
        const data = await checkJobEligibility(jobNo);
        console.log('Eligibility check response:', data);
        setEligibilityData(data);
        setEligibilityStatus('success');
      } catch (error: any) {
        console.error('Error checking job eligibility:', error);
        const errorMessage = error.response?.data?.message || error.message || 'Failed to check eligibility';
        setEligibilityError(errorMessage);
        setEligibilityStatus('error');
      }
    };

    fetchEligibility();
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [jobNo]);

  // Handler for triggering verification
  const handleVerifyDocuments = async () => {
    if (!jobNo) return;

    setIsVerifying(true);
    setVerificationError(null);
    setVerificationTriggered(false);

    try {
      console.log(`Triggering verification for job: ${jobNo}`);
      const response = await triggerVerification(jobNo);
      console.log('Verification trigger response:', response);
      setVerificationTriggered(true);
      // TODO: Optionally start polling for verification status here
    } catch (error: any) {
      console.error('Error triggering verification:', error);
      const errorMessage = error.response?.data?.message || error.message || 'Failed to trigger verification';
      setVerificationError(errorMessage);
    } finally {
      setIsVerifying(false);
    }
  };

  const getDocumentIcon = (type: string) => {
    switch (type) {
      case "pdf": return <FileText className="h-5 w-5 text-red-500" />;
      case "image": return <Image className="h-5 w-5 text-blue-500" />;
      default: return <PaperClip className="h-5 w-5" />;
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>
          {eligibilityStatus === 'success' && eligibilityData?.jobTitle
            ? `${eligibilityData.jobTitle} (${jobNo})`
            : `Job Documents (${jobNo})`}
        </CardTitle>
        {eligibilityStatus === 'success' && eligibilityData?.customerName && (
          <p className="text-sm text-muted-foreground">
            Customer: {eligibilityData.customerName}
          </p>
        )}
      </CardHeader>
      <CardContent>
         {/* --- Eligibility Check Display --- */}
         <div className="mb-4">
          {eligibilityStatus === 'loading' && (
            <div className="flex items-center text-muted-foreground">
              <Loader2 className="mr-2 h-4 w-4 animate-spin" />
              Checking eligibility...
            </div>
          )}
          {eligibilityStatus === 'error' && eligibilityError && (
            <Alert variant="destructive">
              <AlertCircle className="h-4 w-4" />
              <AlertTitle>Eligibility Check Failed</AlertTitle>
              <AlertDescription>{eligibilityError}</AlertDescription>
            </Alert>
          )}
          {eligibilityStatus === 'success' && eligibilityData && (
            // Use "default" variant and rely on icons/text for meaning
            <Alert variant="default">
              {eligibilityData.isEligible
                ? <CheckCircle2 className="h-4 w-4 text-green-500" />
                : <Info className="h-4 w-4 text-yellow-500" />}
              <AlertTitle>
                {eligibilityData.isEligible ? "Ready for Verification" : "Not Ready for Verification"}
              </AlertTitle>
              <AlertDescription>{eligibilityData.message}</AlertDescription>
            </Alert>
          )}
        </div>
        {/* --- End Eligibility Check Display --- */}

        {/* --- Verification Trigger Feedback --- */}
        {verificationTriggered && (
          // Use "default" variant for success message
          <Alert variant="default" className="mb-4">
            <CheckCircle2 className="h-4 w-4 text-green-500" />
            <AlertTitle>Verification Started</AlertTitle>
            <AlertDescription>
              The document verification process has been initiated for Job {jobNo}. You can check the status later.
            </AlertDescription>
          </Alert>
        )}
        {verificationError && (
           <Alert variant="destructive" className="mb-4">
             <AlertCircle className="h-4 w-4" />
             <AlertTitle>Verification Trigger Failed</AlertTitle>
             <AlertDescription>{verificationError}</AlertDescription>
           </Alert>
        )}
         {/* --- End Verification Trigger Feedback --- */}

        <div className="space-y-4">
          <h3 className="text-lg font-semibold mb-2">Required Documents</h3>
          {documents.map((doc) => (
            <div
              key={doc.id}
              className="flex items-center justify-between rounded-lg border p-3"
            >
              <div className="flex items-center gap-3">
                {getDocumentIcon(doc.type)}
                <div>
                  <div className="font-medium">{doc.name}</div>
                </div>
              </div>
              <div className="flex gap-2">
                <Button variant="ghost" size="icon" title="View (Not Implemented)" disabled>
                  <Eye className="h-4 w-4" />
                  <span className="sr-only">View document</span>
                </Button>
                <Button variant="ghost" size="icon" title="Download (Not Implemented)" disabled>
                  <Download className="h-4 w-4" />
                  <span className="sr-only">Download document</span>
                </Button>
              </div>
            </div>
          ))}
        </div>

        {/* --- Verify Button --- */}
        {eligibilityStatus === 'success' && eligibilityData?.isEligible && !verificationTriggered && (
          <div className="mt-6 flex justify-end">
            <Button
              onClick={handleVerifyDocuments} // Should now resolve
              disabled={isVerifying}
            >
              {isVerifying ? (
                <>
                  <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                  Verifying...
                </>
              ) : (
                "Verify Documents"
              )}
            </Button>
          </div>
        )}
        {/* --- End Verify Button --- */}

      </CardContent>
    </Card>
  )
}
