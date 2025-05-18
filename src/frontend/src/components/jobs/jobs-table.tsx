"use client"

import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "../../components/ui/table"
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from "../../components/ui/dropdown-menu"
import { Button } from "../../components/ui/button"
import { Badge } from "../../components/ui/badge"
import { AlertTriangle, CheckCircle, Clock, MoreHorizontal, Loader2 } from "lucide-react" // Added Loader2
import { Link } from "react-router-dom"
import { useState, useEffect } from "react" // Added imports
import { getJobs } from "../../lib/api" // Added import
import { format } from 'date-fns' // Added import

// Define an interface matching the backend JobSummaryDTO
interface JobSummary {
  internalId: number;
  businessCentralJobId: string;
  jobTitle: string;
  customerName: string;
  status: 'PENDING' | 'PROCESSING' | 'VERIFIED' | 'FLAGGED' | 'ERROR'; // Match backend enum
  lastProcessedAt: string; // Assuming ISO string format from backend
}

export function JobsTable() {
  const [jobs, setJobs] = useState<JobSummary[]>([]) // State for fetched jobs
  const [isLoading, setIsLoading] = useState(true) // Loading state
  const [error, setError] = useState<string | null>(null) // Error state

  useEffect(() => {
    const fetchJobs = async () => {
      setIsLoading(true);
      setError(null);
      try {
        const data: JobSummary[] = await getJobs();
        // Sort by lastProcessedAt descending (optional, could be done by backend)
        const sortedData = data.sort((a, b) => {
          const dateA = a.lastProcessedAt ? new Date(a.lastProcessedAt).getTime() : 0;
          const dateB = b.lastProcessedAt ? new Date(b.lastProcessedAt).getTime() : 0;
          return dateB - dateA;
        });
        setJobs(sortedData);
      } catch (err) {
        setError("Failed to fetch jobs.");
        console.error(err);
      } finally {
        setIsLoading(false);
      }
    };

    fetchJobs();
  }, []);

  const getStatusBadge = (status: JobSummary['status']) => { // Use JobSummary status type
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
    <div className="rounded-md border">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Job ID</TableHead>
            <TableHead>Title</TableHead>
            <TableHead>Customer</TableHead>
            <TableHead>Status</TableHead>
            <TableHead>Date</TableHead>
            <TableHead className="w-[50px]"></TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {isLoading ? (
            <TableRow>
              <TableCell colSpan={6} className="h-24 text-center">
                <Loader2 className="mx-auto h-6 w-6 animate-spin text-muted-foreground" />
                <div>Loading jobs...</div>
              </TableCell>
            </TableRow>
          ) : error ? (
             <TableRow>
              <TableCell colSpan={6} className="h-24 text-center text-red-500">
                {error}
              </TableCell>
            </TableRow>
          ) : jobs.length === 0 ? (
             <TableRow>
              <TableCell colSpan={6} className="h-24 text-center text-muted-foreground">
                No jobs found.
              </TableCell>
            </TableRow>
          ) : (
            jobs.map((job) => (
            <TableRow key={job.internalId}> {/* Use internalId as key */}
              <TableCell className="font-medium">
                 {/* Link using internalId */}
                <Link to={`/jobs/${job.internalId}`} className="hover:underline">
                  {job.businessCentralJobId} {/* Display BC Job ID */}
                </Link>
              </TableCell>
              <TableCell>
                <div>{job.jobTitle || ''}</div> {/* Use jobTitle */}
                 {/* Issues are not in JobSummaryDTO, remove this section */}
              </TableCell>
              <TableCell>{job.customerName || ''}</TableCell> {/* Use customerName */}
              <TableCell>{getStatusBadge(job.status)}</TableCell>
              <TableCell>
                {/* Format the date */}
                {job.lastProcessedAt ? format(new Date(job.lastProcessedAt), 'PP') : ''}
              </TableCell>
              <TableCell>
                <DropdownMenu>
                  <DropdownMenuTrigger asChild>
                    <Button variant="ghost" size="icon">
                      <MoreHorizontal className="h-4 w-4" />
                      <span className="sr-only">Open menu for {job.businessCentralJobId}</span>
                    </Button>
                  </DropdownMenuTrigger>
                  <DropdownMenuContent align="end">
                    <DropdownMenuItem asChild>
                       {/* Link using internalId */}
                      <Link to={`/jobs/${job.internalId}`}>View details</Link>
                    </DropdownMenuItem>
                    {/* Add other actions if needed */}
                    {/* <DropdownMenuItem>Export job</DropdownMenuItem> */}
                    {/* <DropdownMenuItem>Print report</DropdownMenuItem> */}
                  </DropdownMenuContent>
                </DropdownMenu>
              </TableCell>
            </TableRow>
          )))}
        </TableBody>
      </Table>
    </div>
  )
}
