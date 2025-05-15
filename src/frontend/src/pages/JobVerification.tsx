"use client"

import { JobVerificationManager } from "@/components/jobs/job-verification-manager"

export default function JobVerification() {
  return (
    <div className="container mx-auto p-8">
      <h1 className="text-2xl font-bold mb-8">Job Verification</h1>
      <JobVerificationManager />
    </div>
  );
}
