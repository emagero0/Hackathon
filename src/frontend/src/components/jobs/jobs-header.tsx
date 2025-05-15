"use client"

import { Button } from "../../components/ui/button";
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from "../../components/ui/dropdown-menu";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle, DialogTrigger, DialogClose } from "../../components/ui/dialog"; // Import Dialog components
import { Input } from "../../components/ui/input"; // Import Input
import { Label } from "../../components/ui/label"; // Import Label
import { ChevronDown, Download, RefreshCw, PlusCircle, Loader2 } from "lucide-react"; // Import PlusCircle, Loader2
import { useState } from "react";
import { toast } from "sonner";
import { requestVerification } from "../../lib/api"; // Use renamed API function


export function JobsHeader() {
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [isDialogOpen, setIsDialogOpen] = useState(false);
  const [jobNoToVerify, setJobNoToVerify] = useState("");
  const [isTriggering, setIsTriggering] = useState(false);


  const handleRefresh = () => {
    setIsRefreshing(true);
    // TODO: Implement actual data refresh logic
    setTimeout(() => setIsRefreshing(false), 1000);
  };

  const handleTriggerVerification = async () => {
    if (!jobNoToVerify.trim()) {
      toast.error("Please enter a Job Number.");
      return;
    }
    setIsTriggering(true);
    try {
      const response = await requestVerification(jobNoToVerify.trim()); // Call renamed API function
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


  return (
    <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
      <div>
        <h1 className="text-3xl font-bold tracking-tight">Jobs</h1>
        <p className="text-muted-foreground">View and manage all verification jobs</p>
      </div>
      <div className="flex flex-wrap gap-2"> {/* Added flex-wrap */}
        {/* Verify New Job Dialog Trigger */}
        <Dialog open={isDialogOpen} onOpenChange={setIsDialogOpen}>
          <DialogTrigger asChild>
            <Button size="sm">
              <PlusCircle className="mr-2 h-4 w-4" />
              Verify New Job
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
                <Label htmlFor="jobNo" className="text-right">
                  Job No
                </Label>
                <Input
                  id="jobNo"
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

        {/* Existing Buttons */}
        <Button variant="outline" size="sm" onClick={handleRefresh} disabled={isRefreshing}>
          <RefreshCw className={`mr-2 h-4 w-4 ${isRefreshing ? "animate-spin" : ""}`} />
          {isRefreshing ? "Refreshing..." : "Refresh"}
        </Button>
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button variant="outline" size="sm">
              <Download className="mr-2 h-4 w-4" />
              Export
              <ChevronDown className="ml-2 h-4 w-4" />
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end">
            <DropdownMenuItem>Export as CSV</DropdownMenuItem>
            <DropdownMenuItem>Export as PDF</DropdownMenuItem>
            <DropdownMenuItem>Export as Excel</DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>
    </div>
  )
}
