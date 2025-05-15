"use client"

import { useState } from 'react';
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
// import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert" // Unused import
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { Checkbox } from "@/components/ui/checkbox"
import { Loader2 } from "lucide-react"
import { toast } from "sonner"
import { triggerVerification } from "@/services/verificationService"
import { StatusBadge } from "./status-badge"

interface JobForVerification {
  jobNo: string;
  addedDate: Date;
  status: 'pending' | 'in_progress' | 'completed' | 'failed';
}

interface VerificationListProps {
  jobs: JobForVerification[];
  onJobsUpdate: (jobs: JobForVerification[]) => void;
}

export function VerificationList({ jobs, onJobsUpdate }: VerificationListProps) {
  const [selectedJobs, setSelectedJobs] = useState<string[]>([]);
  const [verifying, setVerifying] = useState(false);

  const handleVerifySelected = async () => {
    if (selectedJobs.length === 0) return;

    setVerifying(true);

    try {
      // Process each selected job
      for (const jobNo of selectedJobs) {
        await triggerVerification(jobNo);
        // Update job status in the list
        onJobsUpdate(
          jobs.map(job =>
            job.jobNo === jobNo
              ? { ...job, status: 'in_progress' as const }
              : job
          )
        );
      }
      toast.success(`Verification triggered for ${selectedJobs.length} jobs`);
      setSelectedJobs([]); // Clear selection
    } catch (err: any) {
      toast.error(err.response?.data?.message || err.message || 'Failed to trigger verification');
    } finally {
      setVerifying(false);
    }
  };

  const toggleSelectAll = () => {
    if (selectedJobs.length === jobs.length) {
      setSelectedJobs([]);
    } else {
      setSelectedJobs(jobs.map(job => job.jobNo));
    }
  };

  const toggleSelectJob = (jobNo: string) => {
    setSelectedJobs(prev =>
      prev.includes(jobNo)
        ? prev.filter(j => j !== jobNo)
        : [...prev, jobNo]
    );
  };

  // Helper function to format date
  const formatDate = (date: Date) => {
    return new Intl.DateTimeFormat('en-US', {
      dateStyle: 'medium',
      timeStyle: 'short'
    }).format(date);
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle>Jobs for Verification</CardTitle>
      </CardHeader>
      <CardContent>
        <div className="mb-4">
          <Button
            onClick={handleVerifySelected}
            disabled={verifying || selectedJobs.length === 0}
          >
            {verifying ? (
              <>
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                Verifying...
              </>
            ) : (
              `Verify Selected (${selectedJobs.length})`
            )}
          </Button>
        </div>

        <div className="border rounded-md">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead className="w-12">
                  <Checkbox
                    checked={jobs.length > 0 && selectedJobs.length === jobs.length}
                    onCheckedChange={toggleSelectAll}
                  />
                </TableHead>
                <TableHead>Job No</TableHead>
                <TableHead>Added Date</TableHead>
                <TableHead>Status</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {jobs.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={4} className="text-center text-muted-foreground">
                    No jobs added for verification
                  </TableCell>
                </TableRow>
              ) : (
                jobs.map((job) => (
                  <TableRow key={job.jobNo}>
                    <TableCell>
                      <Checkbox
                        checked={selectedJobs.includes(job.jobNo)}
                        onCheckedChange={() => toggleSelectJob(job.jobNo)}
                      />
                    </TableCell>
                    <TableCell>{job.jobNo}</TableCell>
                    <TableCell>{formatDate(job.addedDate)}</TableCell>
                    <TableCell><StatusBadge status={job.status} /></TableCell>
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>
        </div>
      </CardContent>
    </Card>
  );
}
