"use client"

import React, { useState } from 'react';
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Loader2, AlertCircle, CheckCircle2 } from "lucide-react"
import { checkJobEligibility } from "@/services/verificationService"

interface JobEligibilityCheckResponse {
  isEligible: boolean;
  jobNo: string;
  message: string;
}

interface JobEligibilityCheckProps {
  onAddToVerification: (jobNo: string) => void;
}

export function JobEligibilityCheck({ onAddToVerification }: JobEligibilityCheckProps) {
  const [jobNo, setJobNo] = useState('');
  const [checking, setChecking] = useState(false);
  const [result, setResult] = useState<JobEligibilityCheckResponse | null>(null);
  const [error, setError] = useState<string | null>(null);

  const handleCheck = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!jobNo.trim()) return;

    setChecking(true);
    setResult(null);
    setError(null);

    try {
      const response = await checkJobEligibility(jobNo);
      setResult(response);
    } catch (err: any) {
      setError(err.response?.data?.message || err.message || 'Failed to check job eligibility');
    } finally {
      setChecking(false);
    }
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle>Job Eligibility Check</CardTitle>
      </CardHeader>
      <CardContent>
        <form onSubmit={handleCheck} className="space-y-4">
          <div className="flex gap-2">
            <Input
              placeholder="Enter Job Number"
              value={jobNo}
              onChange={(e) => setJobNo(e.target.value)}
              disabled={checking}
            />
            <Button type="submit" disabled={checking || !jobNo.trim()}>
              {checking ? (
                <>
                  <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                  Checking...
                </>
              ) : (
                "Check Eligibility"
              )}
            </Button>
          </div>

          {error && (
            <Alert variant="destructive">
              <AlertCircle className="h-4 w-4" />
              <AlertTitle>Error</AlertTitle>
              <AlertDescription>{error}</AlertDescription>
            </Alert>
          )}

          {result && (
            <Alert variant={result.isEligible ? "default" : "destructive"}>
              {result.isEligible ? (
                <CheckCircle2 className="h-4 w-4 text-green-500" />
              ) : (
                <AlertCircle className="h-4 w-4" />
              )}
              <AlertTitle>
                {result.isEligible ? "Eligible for Verification" : "Not Eligible"}
              </AlertTitle>
              <AlertDescription>{result.message}</AlertDescription>
              {result.isEligible && (
                <Button
                  className="mt-2"
                  onClick={() => onAddToVerification(result.jobNo)}
                >
                  Add to Verification List
                </Button>
              )}
            </Alert>
          )}
        </form>
      </CardContent>
    </Card>
  );
}
