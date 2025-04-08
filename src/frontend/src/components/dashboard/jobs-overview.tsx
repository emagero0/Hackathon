import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "../../components/ui/card"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "../../components/ui/tabs"
import { Badge } from "../../components/ui/badge"
import { Button } from "../../components/ui/button"
import { AlertTriangle, CheckCircle, Clock, Eye, Plus, Search } from "lucide-react"
import { Link } from "react-router-dom"
import { Input } from "../../components/ui/input"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "../../components/ui/select"
import { useState } from "react"

export function JobsOverview() {
  const [searchTerm, setSearchTerm] = useState("")
  const [selectedCategory, setSelectedCategory] = useState("all")
  
  const recentJobs = [
    {
      id: "JOB-1234",
      title: "Sales Quote #SQ-5678",
      customer: "Acme Corp",
      status: "verified",
      date: "2 hours ago",
      amount: "$12,450.00",
      category: "quotes"
    },
    {
      id: "JOB-1235",
      title: "Invoice #INV-9012",
      customer: "Globex Inc",
      status: "flagged",
      date: "3 hours ago",
      amount: "$8,750.00",
      issues: ["Amount mismatch", "Missing signature"],
      category: "invoices"
    },
    {
      id: "JOB-1236",
      title: "Delivery Note #DN-3456",
      customer: "Wayne Enterprises",
      status: "pending",
      date: "5 hours ago",
      amount: "$5,280.00",
      category: "deliveries"
    },
    {
      id: "JOB-1237",
      title: "Purchase Order #PO-7890",
      customer: "Stark Industries",
      status: "verified",
      date: "8 hours ago",
      amount: "$24,999.99",
      category: "orders"
    },
    {
      id: "JOB-1238",
      title: "Credit Note #CN-1122",
      customer: "Oscorp",
      status: "flagged",
      date: "10 hours ago",
      amount: "$1,500.00",
      issues: ["Incorrect tax calculation"],
      category: "credits"
    },
  ]

  const categories = [
    { value: "all", label: "All Categories" },
    { value: "quotes", label: "Quotes" },
    { value: "invoices", label: "Invoices" },
    { value: "deliveries", label: "Deliveries" },
    { value: "orders", label: "Orders" },
    { value: "credits", label: "Credits" },
  ]

  const filteredJobs = recentJobs.filter(job => {
    // Filter by category first
    const categoryMatch = selectedCategory === "all" || job.category === selectedCategory
    
    // Then filter by search term
    const searchMatch = 
      job.title.toLowerCase().includes(searchTerm.toLowerCase()) ||
      job.customer.toLowerCase().includes(searchTerm.toLowerCase()) ||
      job.id.toLowerCase().includes(searchTerm.toLowerCase())
    
    return categoryMatch && searchMatch
  })

  const getStatusFilteredJobs = (status: string) => {
    return filteredJobs.filter(job => job.status === status)
  }

  return (
    <Card className="col-span-2">
      <CardHeader>
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
          <div>
            <CardTitle>Recent Jobs</CardTitle>
            <CardDescription>Overview of recently processed jobs and their verification status</CardDescription>
          </div>
          <Button asChild>
            <Link to="/jobs/new">
              <Plus className="mr-2 h-4 w-4" />
              Add Job
            </Link>
          </Button>
        </div>
      </CardHeader>
      <CardContent>
        <div className="flex flex-col md:flex-row gap-4 mb-6">
          <div className="w-full md:w-64">
            <Select value={selectedCategory} onValueChange={setSelectedCategory}>
              <SelectTrigger>
                <SelectValue placeholder="Select category" />
              </SelectTrigger>
              <SelectContent>
                {categories.map(category => (
                  <SelectItem key={category.value} value={category.value}>
                    {category.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <div className="relative flex-1">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
            <Input
              placeholder="Search jobs..."
              className="pl-8"
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
            />
          </div>
        </div>

        <Tabs defaultValue="all">
          <TabsList className="mb-4">
            <TabsTrigger value="all">All</TabsTrigger>
            <TabsTrigger value="verified">Verified</TabsTrigger>
            <TabsTrigger value="flagged">Flagged</TabsTrigger>
            <TabsTrigger value="pending">Pending</TabsTrigger>
          </TabsList>
          
          <TabsContent value="all" className="space-y-4">
            {filteredJobs.length > 0 ? (
              filteredJobs.map((job) => (
                <JobCard key={job.id} job={job} />
              ))
            ) : (
              <div className="text-center py-8 text-muted-foreground">
                No jobs found matching your criteria
              </div>
            )}
            <div className="flex justify-center">
              <Button variant="outline" asChild>
                <Link to="/jobs">View all jobs</Link>
              </Button>
            </div>
          </TabsContent>
          
          <TabsContent value="verified" className="space-y-4">
            {getStatusFilteredJobs("verified").length > 0 ? (
              getStatusFilteredJobs("verified").map((job) => (
                <JobCard key={job.id} job={job} />
              ))
            ) : (
              <div className="text-center py-8 text-muted-foreground">
                No verified jobs found
              </div>
            )}
          </TabsContent>
          
          <TabsContent value="flagged" className="space-y-4">
            {getStatusFilteredJobs("flagged").length > 0 ? (
              getStatusFilteredJobs("flagged").map((job) => (
                <JobCard key={job.id} job={job} />
              ))
            ) : (
              <div className="text-center py-8 text-muted-foreground">
                No flagged jobs found
              </div>
            )}
          </TabsContent>
          
          <TabsContent value="pending" className="space-y-4">
            {getStatusFilteredJobs("pending").length > 0 ? (
              getStatusFilteredJobs("pending").map((job) => (
                <JobCard key={job.id} job={job} />
              ))
            ) : (
              <div className="text-center py-8 text-muted-foreground">
                No pending jobs found
              </div>
            )}
          </TabsContent>
        </Tabs>
      </CardContent>
    </Card>
  )
}

function JobCard({ job }: { job: any }) {
  return (
    <div className="flex items-center justify-between rounded-lg border p-3">
      <div className="flex items-center gap-4">
        {job.status === "verified" && <CheckCircle className="h-5 w-5 text-green-500" />}
        {job.status === "flagged" && <AlertTriangle className="h-5 w-5 text-red-500" />}
        {job.status === "pending" && <Clock className="h-5 w-5 text-amber-500" />}
        <div>
          <div className="font-medium">{job.title}</div>
          <div className="text-sm text-muted-foreground flex items-center gap-2">
            <span>{job.customer}</span>
            <span>•</span>
            <span>{job.date}</span>
          </div>
          {job.issues && (
            <div className="mt-1 flex flex-wrap gap-1">
              {job.issues.map((issue: string, i: number) => (
                <Badge key={i} variant="destructive" className="text-xs">
                  {issue}
                </Badge>
              ))}
            </div>
          )}
        </div>
      </div>
      <div className="flex items-center gap-4">
        <div className="text-right">
          <div className="font-medium">{job.amount}</div>
          <div className="text-xs text-muted-foreground">{job.id}</div>
        </div>
        <Button variant="ghost" size="icon" asChild>
          <Link to={`/jobs/${job.id}`}>
            <Eye className="h-4 w-4" />
            <span className="sr-only">View job</span>
          </Link>
        </Button>
      </div>
    </div>
  )
}