import { Card, CardContent, CardHeader, CardTitle } from "../../components/ui/card"
import { Badge } from "../../components/ui/badge"
import { AlertTriangle, CheckCircle, Clock } from "lucide-react"
import { Progress } from "../../components/ui/progress"

interface JobVerificationResultsProps {
  jobId: string
}

export function JobVerificationResults({ jobId }: JobVerificationResultsProps) {
  console.log(jobId)
  // This would normally fetch data based on the jobId
  const results = {
    status: "flagged",
    completedAt: "Apr 2, 2023 10:30 AM",
    duration: "45 seconds",
    confidence: 85,
    matches: [
      { field: "Customer Name", expected: "Globex Inc", actual: "Globex Inc", match: true },
      { field: "Invoice Number", expected: "INV-9012", actual: "INV-9012", match: true },
      { field: "Date", expected: "Apr 1, 2023", actual: "Apr 1, 2023", match: true },
      { field: "Amount", expected: "$8,750.00", actual: "$8,950.00", match: false },
      { field: "Tax", expected: "$875.00", actual: "$895.00", match: false },
      { field: "Total", expected: "$9,625.00", actual: "$9,845.00", match: false },
      { field: "Signature", expected: "Present", actual: "Missing", match: false },
    ],
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
          <span>Verification Results</span>
          {getStatusBadge(results.status)}
        </CardTitle>
      </CardHeader>
      <CardContent>
        <div className="space-y-6">
          <div className="grid grid-cols-3 gap-4">
            <div>
              <div className="text-sm font-medium text-muted-foreground">Completed At</div>
              <div>{results.completedAt}</div>
            </div>
            <div>
              <div className="text-sm font-medium text-muted-foreground">Duration</div>
              <div>{results.duration}</div>
            </div>
            <div>
              <div className="text-sm font-medium text-muted-foreground">Confidence</div>
              <div className="flex items-center gap-2">
                <span>{results.confidence}%</span>
                <Progress value={results.confidence} className="h-2 w-20" />
              </div>
            </div>
          </div>

          <div className="rounded-lg border">
            <div className="bg-muted px-4 py-2 rounded-t-lg grid grid-cols-3 font-medium">
              <div>Field</div>
              <div>Expected</div>
              <div>Actual</div>
            </div>
            <div className="divide-y">
              {results.matches.map((match, i) => (
                <div
                  key={i}
                  className={`px-4 py-3 grid grid-cols-3 ${!match.match ? "bg-red-50 dark:bg-red-950/30" : ""}`}
                >
                  <div>{match.field}</div>
                  <div>{match.expected}</div>
                  <div className="flex items-center gap-2">
                    {match.actual}
                    {match.match ? (
                      <CheckCircle className="h-4 w-4 text-green-500" />
                    ) : (
                      <AlertTriangle className="h-4 w-4 text-red-500" />
                    )}
                  </div>
                </div>
              ))}
            </div>
          </div>

          <div className="rounded-lg border p-4 bg-amber-50 dark:bg-amber-950/30">
            <div className="flex items-start gap-2">
              <AlertTriangle className="h-5 w-5 text-amber-500 mt-0.5" />
              <div>
                <div className="font-medium">AI Analysis</div>
                <p className="text-sm mt-1">
                  The document appears to have discrepancies in the financial amounts. The invoice total is calculated
                  incorrectly based on the provided amounts. Additionally, the required signature is missing from the
                  document, which violates company policy for invoices exceeding $5,000.
                </p>
              </div>
            </div>
          </div>
        </div>
      </CardContent>
    </Card>
  )
}

