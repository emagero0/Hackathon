"use client"

import { useState, useEffect, useRef } from 'react';
import { toast } from 'sonner';
import { JobEligibilityCheck } from './job-eligibility-check';
import { VerificationList } from './verification-list';
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Switch } from "@/components/ui/switch";
import { Loader2 } from "lucide-react";
import { fetchJobsPendingSecondCheck, JobPendingSecondCheck } from '@/services/verificationService';

interface Job {
  jobNo: string;
  addedDate: Date;
  status: 'pending' | 'in_progress' | 'completed' | 'failed';
}

export function JobVerificationManager() {
  const [jobs, setJobs] = useState<Job[]>([]);
  const [autoFetch, setAutoFetch] = useState<boolean>(false);
  const [fetchInterval, setFetchInterval] = useState<number>(5);
  const [isFetching, setIsFetching] = useState<boolean>(false);
  const intervalRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const handleAddToVerification = (jobNo: string) => {
    console.log('handleAddToVerification called with jobNo:', jobNo);

    // Make sure we have a valid job number
    if (!jobNo) {
      console.error('Attempted to add job with no job number');
      toast.error("Cannot add job with no job number");
      return;
    }

    // Ensure job number is a string and trimmed
    const cleanJobNo = String(jobNo).trim();

    if (!cleanJobNo) {
      console.error('Attempted to add job with empty job number after trimming');
      toast.error("Cannot add job with empty job number");
      return;
    }

    // Check if job is already in the list
    if (jobs.some(job => job.jobNo === cleanJobNo)) {
      // Show an error toast using sonner
      toast.error("Job is already in the verification list");
      return;
    }

    // Add new job to list
    const newJob: Job = {
      jobNo: cleanJobNo,
      addedDate: new Date(),
      status: 'pending'
    };

    console.log('Adding new job to verification list:', newJob);
    setJobs(prevJobs => {
      const updatedJobs = [...prevJobs, newJob];
      console.log('Updated jobs list:', updatedJobs);
      return updatedJobs;
    });

    // Show a success toast
    toast.success(`Added job ${cleanJobNo} to verification list`);
  };

  const fetchPendingJobs = async () => {
    if (isFetching) return;

    setIsFetching(true);
    try {
      // Clear existing jobs first to ensure we get a fresh list
      setJobs([]);

      const pendingJobs = await fetchJobsPendingSecondCheck();
      console.log('Fetched pending jobs:', pendingJobs);

      // Add new jobs to the list if they're not already there
      let newJobsAdded = 0;

      // Debug current jobs
      console.log('Current jobs before adding new ones:', jobs);

      if (!Array.isArray(pendingJobs)) {
        console.error('pendingJobs is not an array:', pendingJobs);
        toast.error("Invalid response from server");
        return;
      }

      // Force a direct update of the jobs state with the new jobs
      const formattedJobs = pendingJobs.map((pendingJob: JobPendingSecondCheck) => {
        console.log('Processing pending job:', pendingJob);

        // Make sure we have a valid job number
        if (!pendingJob.no) {
          console.error('Pending job has no job number:', pendingJob);
          return null;
        }

        // Ensure job number is a string
        const jobNo = String(pendingJob.no).trim();

        if (!jobNo) {
          console.error('Pending job has empty job number after trimming:', pendingJob);
          return null;
        }

        const newJob: Job = {
          jobNo: jobNo,
          addedDate: new Date(),
          status: 'pending'
        };

        console.log('Adding job to list:', newJob);
        newJobsAdded++;
        return newJob;
      }).filter(job => job !== null) as Job[];

      console.log(`Adding ${formattedJobs.length} jobs to the list`);

      // Set the jobs directly instead of merging with existing jobs
      setJobs(formattedJobs);

      // Log the final jobs list
      console.log('Final jobs list:', formattedJobs);

      if (newJobsAdded > 0) {
        toast.success(`Added ${newJobsAdded} new job${newJobsAdded > 1 ? 's' : ''} to verification list`);
      } else {
        toast.info("No new jobs found for verification");
      }
    } catch (error) {
      console.error('Error fetching pending jobs:', error);
      toast.error("Failed to fetch pending jobs");
    } finally {
      setIsFetching(false);
    }
  };

  const toggleAutoFetch = (checked: boolean) => {
    setAutoFetch(checked);

    // Save setting to localStorage
    localStorage.setItem('autoFetchEnabled', checked.toString());

    if (!checked && intervalRef.current) {
      clearInterval(intervalRef.current);
      intervalRef.current = null;
    }
  };

  const handleIntervalChange = (value: string) => {
    const interval = parseInt(value);
    if (!isNaN(interval) && interval > 0) {
      setFetchInterval(interval);
      localStorage.setItem('fetchInterval', interval.toString());

      // Restart interval if auto-fetch is enabled
      if (autoFetch && intervalRef.current) {
        clearInterval(intervalRef.current);
        intervalRef.current = setInterval(fetchPendingJobs, interval * 60 * 1000);
      }
    }
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
        console.log('Loaded saved jobs from localStorage:', parsedJobs);
        setJobs(parsedJobs);
      } catch (error) {
        console.error('Error loading saved jobs:', error);
      }
    }

    // Load auto-fetch settings
    const savedAutoFetch = localStorage.getItem('autoFetchEnabled');
    if (savedAutoFetch) {
      setAutoFetch(savedAutoFetch === 'true');
    }

    const savedInterval = localStorage.getItem('fetchInterval');
    if (savedInterval) {
      const interval = parseInt(savedInterval);
      if (!isNaN(interval) && interval > 0) {
        setFetchInterval(interval);
      }
    }
  }, []);

  useEffect(() => {
    // Save jobs to localStorage whenever they change
    localStorage.setItem('verificationJobs', JSON.stringify(jobs));
  }, [jobs]);

  useEffect(() => {
    // Set up interval for auto-fetching
    if (autoFetch) {
      fetchPendingJobs(); // Fetch immediately when enabled
      intervalRef.current = setInterval(fetchPendingJobs, fetchInterval * 60 * 1000);
    }

    return () => {
      if (intervalRef.current) {
        clearInterval(intervalRef.current);
      }
    };
  }, [autoFetch, fetchInterval]);

  return (
    <div className="space-y-8">
      <Card>
        <CardHeader>
          <CardTitle>Job Fetch Settings</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="flex items-center justify-between">
            <div>
              <div className="flex gap-2">
                <Button
                  onClick={fetchPendingJobs}
                  disabled={isFetching}
                >
                  {isFetching ? (
                    <>
                      <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                      Fetching...
                    </>
                  ) : (
                    "Fetch Pending Jobs Now"
                  )}
                </Button>

                <Button
                  variant="outline"
                  onClick={() => {
                    // Clear localStorage and refresh the page
                    localStorage.removeItem('verificationJobs');
                    window.location.reload();
                  }}
                >
                  Reset & Refresh
                </Button>
              </div>
              <p className="text-sm text-muted-foreground mt-1">
                Fetch jobs from Business Central that are pending second check
              </p>
            </div>
          </div>

          <div className="flex items-center justify-between">
            <div className="space-y-0.5">
              <Label htmlFor="autoFetch">Auto-Fetch Jobs</Label>
              <div className="text-sm text-muted-foreground">Automatically fetch pending jobs at regular intervals</div>
            </div>
            <Switch
              id="autoFetch"
              checked={autoFetch}
              onCheckedChange={toggleAutoFetch}
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor="fetchInterval">Fetch Interval (minutes)</Label>
            <Input
              id="fetchInterval"
              type="number"
              value={fetchInterval}
              onChange={(e) => handleIntervalChange(e.target.value)}
              disabled={!autoFetch}
              min={1}
            />
          </div>
        </CardContent>
      </Card>

      <JobEligibilityCheck onAddToVerification={handleAddToVerification} />
      <VerificationList jobs={jobs} onJobsUpdate={setJobs} />
    </div>
  );
}
