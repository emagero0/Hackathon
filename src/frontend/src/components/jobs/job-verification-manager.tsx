"use client"

import { useState, useEffect } from 'react';
import { toast } from 'sonner';
import { JobEligibilityCheck } from './job-eligibility-check';
import { VerificationList } from './verification-list';
// import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert" // Unused import

interface Job {
  jobNo: string;
  addedDate: Date;
  status: 'pending' | 'in_progress' | 'completed' | 'failed';
}

export function JobVerificationManager() {
  const [jobs, setJobs] = useState<Job[]>([]);

  const handleAddToVerification = (jobNo: string) => {
    // Check if job is already in the list
    if (jobs.some(job => job.jobNo === jobNo)) {
      // Show an error toast using sonner
      toast.error("Job is already in the verification list");
      return;
    }

    // Add new job to list
    const newJob: Job = {
      jobNo,
      addedDate: new Date(),
      status: 'pending'
    };

    setJobs(prevJobs => [...prevJobs, newJob]);
    // Show a success toast
    toast.success(`Added job ${jobNo} to verification list`);
  };

  useEffect(() => {
    // Load saved jobs from localStorage on mount
    const savedJobs = localStorage.getItem('verificationJobs');
    if (savedJobs) {
      try {
        const parsedJobs = JSON.parse(savedJobs).map((job: Job) => ({
          ...job,
          addedDate: new Date(job.addedDate)
        }));
        setJobs(parsedJobs);
      } catch (error) {
        console.error('Error loading saved jobs:', error);
      }
    }
  }, []);

  useEffect(() => {
    // Save jobs to localStorage whenever they change
    localStorage.setItem('verificationJobs', JSON.stringify(jobs));
  }, [jobs]);

  return (
    <div className="space-y-8">
      <JobEligibilityCheck onAddToVerification={handleAddToVerification} />
      <VerificationList jobs={jobs} onJobsUpdate={setJobs} />
    </div>
  );
}
