import { Card, CardContent, CardHeader, CardTitle } from "../../components/ui/card"
import { Badge } from "../../components/ui/badge"
import { AlertTriangle, CheckCircle, Clock } from "lucide-react"

interface JobDetailsProps {
  jobId: string
}

export function JobDetails({ jobId }: JobDetailsProps) {
  // This would normally fetch data based on the jobId
  const job = {
    id: jobId,
    title: "Invoice #INV-9012",
    customer: "Globex Inc",
    status: "flagged",
    date: "Apr 2, 2023",
    amount: "$8,750.00",
    contactName: "Jane Smith",
    contactEmail: "jane.smith@globex.com",
    contactPhone: "+1 (555) 123-4567",
    reference: "REF-2023-0456",
    department: "Sales",
    assignedTo: "John Doe",
    createdAt: "Apr 2, 2023 09:45 AM",
    updatedAt: "Apr 2, 2023 10:30 AM",
    issues: ["Amount mismatch", "Missing signature"],
  }

  const getStatusBadge = (status: string) => {
    switch (status) {
      case "verified":
        return (
          <Badge
            variant="outline"
            className="bg-green-50 text-green-700 border-green-200 dark:bg-green-950 dark:text-green-400 dark:border-green-800"
          >
            <CheckCircle className="mr-1 h-3 w-3" />
            Verified
          </Badge>
        )
      case "flagged":
        return (
          <Badge
            variant="outline"
            className="bg-red-50 text-red-700 border-red-200 dark:bg-red-950 dark:text-red-400 dark:border-red-800"
          >
            <AlertTriangle className="mr-1 h-3 w-3" />
            Flagged
          </Badge>
        )
      case "pending":
        return (
          <Badge
            variant="outline"
            className="bg-amber-50 text-amber-700 border-amber-200 dark:bg-amber-950 dark:text-amber-400 dark:border-amber-800"
          >
            <Clock className="mr-1 h-3 w-3" />
            Pending
          </Badge>
        )
      default:
        return <Badge variant="outline">{status}</Badge>
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center justify-between">
          <span>Job Details</span>
          {getStatusBadge(job.status)}
        </CardTitle>
      </CardHeader>
      <CardContent>
        <div className="space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <div>
              <div className="text-sm font-medium text-muted-foreground">Job ID</div>
              <div>{job.id}</div>
            </div>
            <div>
              <div className="text-sm font-medium text-muted-foreground">Title</div>
              <div>{job.title}</div>
            </div>
            <div>
              <div className="text-sm font-medium text-muted-foreground">Customer</div>
              <div>{job.customer}</div>
            </div>
            <div>
              <div className="text-sm font-medium text-muted-foreground">Amount</div>
              <div>{job.amount}</div>
            </div>
            <div>
              <div className="text-sm font-medium text-muted-foreground">Reference</div>
              <div>{job.reference}</div>
            </div>
            <div>
              <div className="text-sm font-medium text-muted-foreground">Department</div>
              <div>{job.department}</div>
            </div>
            <div>
              <div className="text-sm font-medium text-muted-foreground">Assigned To</div>
              <div>{job.assignedTo}</div>
            </div>
            <div>
              <div className="text-sm font-medium text-muted-foreground">Created</div>
              <div>{job.createdAt}</div>
            </div>
          </div>

          <div className="pt-4 border-t">
            <div className="text-sm font-medium mb-2">Contact Information</div>
            <div className="grid grid-cols-2 gap-4">
              <div>
                <div className="text-sm font-medium text-muted-foreground">Name</div>
                <div>{job.contactName}</div>
              </div>
              <div>
                <div className="text-sm font-medium text-muted-foreground">Email</div>
                <div>{job.contactEmail}</div>
              </div>
              <div>
                <div className="text-sm font-medium text-muted-foreground">Phone</div>
                <div>{job.contactPhone}</div>
              </div>
            </div>
          </div>

          {job.issues && (
            <div className="pt-4 border-t">
              <div className="text-sm font-medium mb-2 text-red-500">Flagged Issues</div>
              <div className="space-y-2">
                {job.issues.map((issue, i) => (
                  <Badge key={i} variant="destructive" className="mr-2">
                    {issue}
                  </Badge>
                ))}
              </div>
            </div>
          )}
        </div>
      </CardContent>
    </Card>
  )
}

