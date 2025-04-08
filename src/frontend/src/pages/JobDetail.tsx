"use client"

import { useParams, Link } from "react-router-dom"
import { JobDetails } from "../components/jobs/job-details"
import { JobDocuments } from "../components/jobs/job-documents"
import { JobVerificationResults } from "../components/jobs/job-verification-results"
import { JobFeedback } from "../components/jobs/job-feedback"
import { Button } from "../components/ui/button"
import { ArrowLeft } from "lucide-react"

export default function JobDetail() {
  const { id } = useParams<{ id: string }>()

  return (
    <main className="flex min-h-screen flex-col p-6">
      <div className="flex items-center gap-4 mb-6">
        <Button variant="outline" size="icon" asChild>
          <Link to="/jobs">
            <ArrowLeft className="h-4 w-4" />
            <span className="sr-only">Back to jobs</span>
          </Link>
        </Button>
        <h1 className="text-2xl font-bold">Job #{id}</h1>
      </div>

      <div className="grid gap-6 md:grid-cols-2">
        <div>
          <JobDetails jobId={id || ""} />
        </div>
        <div>
          <JobDocuments jobId={id || ""} />
        </div>
      </div>

      <div className="mt-6">
        <JobVerificationResults jobId={id || ""} />
      </div>

      <div className="mt-6">
        <JobFeedback jobId={id || ""} />
      </div>
    </main>
  )
}

