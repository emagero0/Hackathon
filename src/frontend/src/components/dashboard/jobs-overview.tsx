import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "../../components/ui/card";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "../../components/ui/tabs";
import { Button } from "../../components/ui/button";
import { AlertTriangle, CheckCircle, Clock, Eye, PlusCircle, Search, Loader2 } from "lucide-react"; // Changed Plus to PlusCircle, Added Loader2
import { Link } from "react-router-dom";
import { Input } from "../../components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "../../components/ui/select";
import { useState, useEffect } from "react";
import { getJobs, requestVerification } from "../../lib/api"; // Import requestVerification
import { formatDistanceToNow } from 'date-fns';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle, DialogTrigger, DialogClose } from "../../components/ui/dialog"; // Import Dialog components
import { Label } from "../../components/ui/label"; // Import Label
import { toast } from "sonner"; // Import toast

// Define an interface matching the backend JobSummaryDTO
interface JobSummary {
  internalId: number;
  businessCentralJobId: string;
  jobTitle: string;
  customerName: string;
  status: 'PENDING' | 'PROCESSING' | 'VERIFIED' | 'FLAGGED' | 'ERROR'; // Match backend enum
  lastProcessedAt: string; // Assuming ISO string format from backend
}

// Define an interface for the Category data structure (can be removed if filtering is done differently)
// interface Category {
//   value: string;
//   label: string;
// }

export function JobsOverview() {
  const [searchTerm, setSearchTerm] = useState("");
  const [selectedCategory, setSelectedCategory] = useState("all");
  const [jobs, setJobs] = useState<JobSummary[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // State for the verification dialog
  const [isDialogOpen, setIsDialogOpen] = useState(false);
  const [jobNoToVerify, setJobNoToVerify] = useState("");
  const [isTriggering, setIsTriggering] = useState(false);


  useEffect(() => {
    const fetchJobs = async () => {
      setIsLoading(true);
      setError(null);
      try {
        const data: JobSummary[] = await getJobs();
        // Sort by lastProcessedAt descending to get recent ones
        const sortedData = data.sort((a, b) => {
          // Handle potential null or invalid dates gracefully
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

  const handleTriggerVerification = async () => {
    if (!jobNoToVerify.trim()) {
      toast.error("Please enter a Job Number.");
      return;
    }
    setIsTriggering(true);
    try {
      const response = await requestVerification(jobNoToVerify.trim());
      toast.success(`Verification requested (ID: ${response.verificationRequestId}) for Job ${jobNoToVerify.trim()}.`);
      setJobNoToVerify(""); // Clear input
      setIsDialogOpen(false); // Close dialog
    } catch (error) {
      console.error("Failed to trigger verification:", error);
      toast.error("Failed to initiate verification. Please try again.");
    } finally {
      setIsTriggering(false);
    }
  };

  // Keep categories for the dropdown for now
  const categories = [
    { value: "all", label: "All Categories" },
    { value: "quotes", label: "Quotes" },
    { value: "invoices", label: "Invoices" },
    { value: "deliveries", label: "Deliveries" },
    { value: "orders", label: "Orders" },
    { value: "credits", label: "Credits" },
  ]

  // Filter based on fetched jobs state
  const filteredJobs = jobs.filter(job => {
    // TODO: Update category filtering if needed, or remove if not used
    // For now, category filtering is removed as JobSummaryDTO doesn't have category
    const categoryMatch = true;

    // Filter by search term
    const searchMatch =
      (job.jobTitle?.toLowerCase() || '').includes(searchTerm.toLowerCase()) ||
      (job.customerName?.toLowerCase() || '').includes(searchTerm.toLowerCase()) ||
      (job.businessCentralJobId?.toLowerCase() || '').includes(searchTerm.toLowerCase());

    return categoryMatch && searchMatch;
  });

  // Filter by status based on fetched jobs state
  const getStatusFilteredJobs = (status: JobSummary['status'] | JobSummary['status'][]) => {
    const statuses = Array.isArray(status) ? status : [status];
    return filteredJobs.filter(job => statuses.includes(job.status));
  }

  return (
    <Card className="col-span-2">
      <CardHeader>
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
          <div>
            <CardTitle>Recent Jobs</CardTitle>
            <CardDescription>Overview of recently processed jobs and their verification status</CardDescription>
          </div>
          {/* Changed Button to trigger Dialog */}
          <Dialog open={isDialogOpen} onOpenChange={setIsDialogOpen}>
            <DialogTrigger asChild>
              <Button size="sm">
                <PlusCircle className="mr-2 h-4 w-4" />
                Verify Job
              </Button>
            </DialogTrigger>
            <DialogContent className="sm:max-w-[425px]">
              <DialogHeader>
                <DialogTitle>Verify New Job</DialogTitle>
                <DialogDescription>
                  Enter the Business Central Job Number to start the document verification process.
                </DialogDescription>
              </DialogHeader>
              <div className="grid gap-4 py-4">
                <div className="grid grid-cols-4 items-center gap-4">
                  <Label htmlFor="jobNoDash" className="text-right"> {/* Changed id */}
                    Job No
                  </Label>
                  <Input
                    id="jobNoDash" // Changed id
                    value={jobNoToVerify}
                    onChange={(e) => setJobNoToVerify(e.target.value)}
                    className="col-span-3"
                    placeholder="e.g., J069023"
                  />
                </div>
              </div>
              <DialogFooter>
                 <DialogClose asChild>
                   <Button type="button" variant="outline" disabled={isTriggering}>
                     Cancel
                   </Button>
                 </DialogClose>
                <Button type="button" onClick={handleTriggerVerification} disabled={isTriggering}>
                  {isTriggering && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
                  Start Verification
                </Button>
              </DialogFooter>
            </DialogContent>
          </Dialog>
        </div>
      </CardHeader>
      <CardContent>
        <div className="flex flex-col md:flex-row gap-4 mb-6">
          {/* Keep category select for now, might remove later */}
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
            {isLoading ? (
              <div className="text-center py-8 text-muted-foreground">Loading jobs...</div>
            ) : error ? (
              <div className="text-center py-8 text-red-500">{error}</div>
            ) : filteredJobs.length > 0 ? (
              // Display only the 5 most recent jobs in this overview
              filteredJobs.slice(0, 5).map((job) => (
                <JobCard key={job.internalId} job={job} />
              ))
            ) : (
              <div className="text-center py-8 text-muted-foreground">
                No recent jobs found matching your criteria
              </div>
            )}
            <div className="flex justify-center mt-4">
              <Button variant="outline" asChild>
                <Link to="/jobs">View all jobs</Link>
              </Button>
            </div>
          </TabsContent>

          <TabsContent value="verified" className="space-y-4">
             {isLoading ? (
              <div className="text-center py-8 text-muted-foreground">Loading jobs...</div>
            ) : error ? (
              <div className="text-center py-8 text-red-500">{error}</div>
            ) : getStatusFilteredJobs("VERIFIED").length > 0 ? (
              getStatusFilteredJobs("VERIFIED").slice(0, 5).map((job) => ( // Limit display
                <JobCard key={job.internalId} job={job} />
              ))
            ) : (
              <div className="text-center py-8 text-muted-foreground">
                No recent verified jobs found
              </div>
            )}
          </TabsContent>

          <TabsContent value="flagged" className="space-y-4">
             {isLoading ? (
              <div className="text-center py-8 text-muted-foreground">Loading jobs...</div>
            ) : error ? (
              <div className="text-center py-8 text-red-500">{error}</div>
            ) : getStatusFilteredJobs("FLAGGED").length > 0 ? (
              getStatusFilteredJobs("FLAGGED").slice(0, 5).map((job) => ( // Limit display
                <JobCard key={job.internalId} job={job} />
              ))
            ) : (
              <div className="text-center py-8 text-muted-foreground">
                No recent flagged jobs found
              </div>
            )}
          </TabsContent>

          <TabsContent value="pending" className="space-y-4">
             {isLoading ? (
              <div className="text-center py-8 text-muted-foreground">Loading jobs...</div>
            ) : error ? (
              <div className="text-center py-8 text-red-500">{error}</div>
            ) : getStatusFilteredJobs(["PENDING", "PROCESSING"]).length > 0 ? (
               // Combine PENDING and PROCESSING, sort again, then slice
              getStatusFilteredJobs(["PENDING", "PROCESSING"])
                .sort((a, b) => {
                   const dateA = a.lastProcessedAt ? new Date(a.lastProcessedAt).getTime() : 0;
                   const dateB = b.lastProcessedAt ? new Date(b.lastProcessedAt).getTime() : 0;
                   return dateB - dateA;
                 })
                .slice(0, 5)
                .map((job) => (
                  <JobCard key={job.internalId} job={job} />
                ))
            ) : (
              <div className="text-center py-8 text-muted-foreground">
                No recent pending jobs found
              </div>
            )}
          </TabsContent>
        </Tabs>
      </CardContent>
    </Card>
  )
}

// Update JobCard to use JobSummary interface and format data
function JobCard({ job }: { job: JobSummary }) {
  const formatRelativeDate = (dateString: string | null) => {
    if (!dateString) return '';
    try {
      // Ensure date-fns is installed or use native Date formatting
      return formatDistanceToNow(new Date(dateString), { addSuffix: true });
    } catch (e) {
      console.error("Error formatting date:", e);
      return dateString; // Fallback to original string
    }
  };

  return (
    <div className="flex items-center justify-between rounded-lg border p-3">
      <div className="flex items-center gap-4 overflow-hidden"> {/* Added overflow-hidden */}
        {/* Map backend status to icons/colors */}
        {job.status === "VERIFIED" && <CheckCircle className="h-5 w-5 text-green-500 flex-shrink-0" />}
        {job.status === "FLAGGED" && <AlertTriangle className="h-5 w-5 text-red-500 flex-shrink-0" />}
        {(job.status === "PENDING" || job.status === "PROCESSING") && <Clock className="h-5 w-5 text-amber-500 flex-shrink-0" />}
        {job.status === "ERROR" && <AlertTriangle className="h-5 w-5 text-destructive flex-shrink-0" />} {/* Added Error status */}

        <div className="flex-1 overflow-hidden"> {/* Added flex-1 and overflow-hidden */}
          <div className="font-medium truncate" title={job.jobTitle}>{job.jobTitle || ''}</div>
          <div className="text-sm text-muted-foreground flex items-center gap-2 flex-wrap">
            <span className="truncate" title={job.customerName}>{job.customerName || ''}</span>
            <span>•</span>
            <span>{formatRelativeDate(job.lastProcessedAt)}</span>
          </div>
          {/* Issues are not in JobSummaryDTO, remove this section */}
        </div>
      </div>
      <div className="flex items-center gap-2 sm:gap-4 flex-shrink-0"> {/* Added flex-shrink-0 */}
        <div className="text-right hidden sm:block"> {/* Hide ID on smaller screens */}
          {/* Amount is not in JobSummaryDTO, display BC Job ID */}
          <div className="text-xs text-muted-foreground">{job.businessCentralJobId}</div>
        </div>
        <Button variant="ghost" size="icon" asChild>
          {/* Link using internalId */}
          <Link to={`/jobs/${job.internalId}`}>
            <Eye className="h-4 w-4" />
            <span className="sr-only">View job {job.businessCentralJobId}</span>
          </Link>
        </Button>
      </div>
    </div>
  )
}
