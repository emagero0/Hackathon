"use client"

import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "../../components/ui/table"
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from "../../components/ui/dropdown-menu"
import { Button } from "../../components/ui/button"
import { Badge } from "../../components/ui/badge"
import { AlertTriangle, CheckCircle, Clock, MoreHorizontal } from "lucide-react"
import { Link } from "react-router-dom"

export function JobsTable() {
  const jobs = [
    {
      id: "JOB-1234",
      title: "Sales Quote #SQ-5678",
      customer: "Acme Corp",
      status: "verified",
      date: "Apr 2, 2023",
      amount: "$12,450.00",
    },
    {
      id: "JOB-1235",
      title: "Invoice #INV-9012",
      customer: "Globex Inc",
      status: "flagged",
      date: "Apr 2, 2023",
      amount: "$8,750.00",
      issues: ["Amount mismatch", "Missing signature"],
    },
    {
      id: "JOB-1236",
      title: "Delivery Note #DN-3456",
      customer: "Wayne Enterprises",
      status: "pending",
      date: "Apr 2, 2023",
      amount: "$5,280.00",
    },
    {
      id: "JOB-1237",
      title: "Purchase Order #PO-7890",
      customer: "Stark Industries",
      status: "verified",
      date: "Apr 1, 2023",
      amount: "$24,999.99",
    },
    {
      id: "JOB-1238",
      title: "Credit Note #CN-1122",
      customer: "Oscorp",
      status: "flagged",
      date: "Apr 1, 2023",
      amount: "$1,500.00",
      issues: ["Incorrect tax calculation"],
    },
    {
      id: "JOB-1239",
      title: "Sales Quote #SQ-3344",
      customer: "LexCorp",
      status: "verified",
      date: "Mar 31, 2023",
      amount: "$18,200.00",
    },
    {
      id: "JOB-1240",
      title: "Invoice #INV-5566",
      customer: "Umbrella Corp",
      status: "pending",
      date: "Mar 31, 2023",
      amount: "$7,300.00",
    },
    {
      id: "JOB-1241",
      title: "Purchase Order #PO-7788",
      customer: "Cyberdyne Systems",
      status: "verified",
      date: "Mar 30, 2023",
      amount: "$32,100.00",
    },
    {
      id: "JOB-1242",
      title: "Delivery Note #DN-9900",
      customer: "Weyland-Yutani",
      status: "flagged",
      date: "Mar 30, 2023",
      amount: "$9,450.00",
      issues: ["Quantity mismatch", "Wrong delivery address"],
    },
    {
      id: "JOB-1243",
      title: "Credit Note #CN-1122",
      customer: "Massive Dynamic",
      status: "verified",
      date: "Mar 29, 2023",
      amount: "$2,800.00",
    },
  ]

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
    <div className="rounded-md border">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Job ID</TableHead>
            <TableHead>Title</TableHead>
            <TableHead>Customer</TableHead>
            <TableHead>Status</TableHead>
            <TableHead>Date</TableHead>
            <TableHead className="text-right">Amount</TableHead>
            <TableHead className="w-[50px]"></TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {jobs.map((job) => (
            <TableRow key={job.id}>
              <TableCell className="font-medium">
                <Link to={`/jobs/${job.id}`} className="hover:underline">
                  {job.id}
                </Link>
              </TableCell>
              <TableCell>
                <div>{job.title}</div>
                {job.issues && (
                  <div className="mt-1 flex flex-wrap gap-1">
                    {job.issues.map((issue, i) => (
                      <Badge key={i} variant="destructive" className="text-xs">
                        {issue}
                      </Badge>
                    ))}
                  </div>
                )}
              </TableCell>
              <TableCell>{job.customer}</TableCell>
              <TableCell>{getStatusBadge(job.status)}</TableCell>
              <TableCell>{job.date}</TableCell>
              <TableCell className="text-right">{job.amount}</TableCell>
              <TableCell>
                <DropdownMenu>
                  <DropdownMenuTrigger asChild>
                    <Button variant="ghost" size="icon">
                      <MoreHorizontal className="h-4 w-4" />
                      <span className="sr-only">Open menu</span>
                    </Button>
                  </DropdownMenuTrigger>
                  <DropdownMenuContent align="end">
                    <DropdownMenuItem asChild>
                      <Link to={`/jobs/${job.id}`}>View details</Link>
                    </DropdownMenuItem>
                    <DropdownMenuItem>Export job</DropdownMenuItem>
                    <DropdownMenuItem>Print report</DropdownMenuItem>
                  </DropdownMenuContent>
                </DropdownMenu>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  )
}

