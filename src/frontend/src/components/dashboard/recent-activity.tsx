"use client"

import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "../../components/ui/card";
import { Avatar, AvatarFallback } from "../../components/ui/avatar";
import { Button } from "../../components/ui/button"; // Import Button
import { AlertTriangle, CheckCircle, FileText, MessageSquare, DatabaseZap, RefreshCw } from "lucide-react"; // Added RefreshCw
import { useState, useEffect, useCallback } from "react"; // Added useCallback
import { getActivityLog } from "../../lib/api";
import { formatDistanceToNow } from 'date-fns';
import { Link } from "react-router-dom"; // Added import
import { Skeleton } from "../ui/skeleton"; // Import Skeleton

// Define interface matching backend ActivityLogDTO
interface ActivityLogData {
  id: number;
  timestamp: string; // Assuming ISO string
  eventType: string;
  description: string;
  relatedJobId: number | null;
  userIdentifier: string | null;
}

export function RecentActivity() {
    const [activities, setActivities] = useState<ActivityLogData[]>([])
    const [isLoading, setIsLoading] = useState(true)
    const [error, setError] = useState<string | null>(null)

    // Wrap fetch logic in useCallback so the function identity is stable
    const fetchActivities = useCallback(async () => {
      setIsLoading(true);
      setError(null);
      try {
        // Fetch the first page of activities (e.g., 5 items)
        const data = await getActivityLog(0, 5); // Fetch page 0, size 5
        console.log("Fetched Activity Log Data:", data); // Keep console log for debugging
        // Ensure data.content exists and is an array before setting state
        setActivities(data && Array.isArray(data.content) ? data.content : []);
      } catch (err) {
        setError("Failed to fetch recent activity.");
        console.error(err);
      } finally {
        setIsLoading(false);
      }
    }, []); // Empty dependency array for useCallback

    useEffect(() => {
      fetchActivities(); // Fetch on initial mount
    }, [fetchActivities]); // Depend on the memoized fetchActivities function

    // Map backend eventType to icons
    const getActionIcon = (eventType: string) => {
        switch (eventType) {
            case "JOB_PROCESSED":
                 // Distinguish based on description content if needed, or use a generic icon
                 // For now, using CheckCircle for any JOB_PROCESSED
                 return <CheckCircle className="h-5 w-5 text-green-500" />
            // Removed specific VERIFIED/FLAGGED cases as we use JOB_PROCESSED now
            case "BC_UPDATE_FAILURE":
            case "ERROR": // Use the constant from ActivityLogService
                return <AlertTriangle className="h-5 w-5 text-red-500" />
            // Removed specific PROCESSING/NLP/OCR cases
            case "BC_UPDATE_SUCCESS":
                return <DatabaseZap className="h-5 w-5 text-blue-500" />
            case "FEEDBACK_SUBMITTED":
                return <MessageSquare className="h-5 w-5 text-purple-500" />
            // Add more specific cases if new event types are added in ActivityLogService
            default:
                return <FileText className="h-5 w-5 text-muted-foreground" /> // Default icon
        }
    }

    // Display description directly from the log
    const getActionText = (activity: ActivityLogData) => {
        return <span>{activity.description}</span>;
    }

    return (
        <Card>
            <CardHeader className="flex flex-row items-center justify-between"> {/* Added flex layout */}
                <div>
                    <CardTitle>Recent Activity</CardTitle>
                    <CardDescription>Latest actions and updates from the verification system</CardDescription>
                </div>
                <Button variant="outline" size="icon" onClick={fetchActivities} disabled={isLoading}>
                    <RefreshCw className={`h-4 w-4 ${isLoading ? "animate-spin" : ""}`} />
                    <span className="sr-only">Refresh Activity</span>
                </Button>
            </CardHeader>
            <CardContent>
                 {isLoading ? (
                    // Use Skeleton loaders for better UX
                    <div className="space-y-8">
                        {[...Array(3)].map((_, i) => ( // Show 3 skeletons
                             <div key={i} className="flex">
                                <Skeleton className="h-10 w-10 rounded-full mr-4" />
                                <div className="flex-1 space-y-2">
                                    <Skeleton className="h-4 w-3/4" />
                                    <Skeleton className="h-3 w-1/2" />
                                </div>
                            </div>
                        ))}
                    </div>
                 ) : error ? (
                    <div className="text-center py-8 text-red-500">{error}</div>
                 ) : activities.length === 0 ? (
                     <div className="text-center py-8 text-muted-foreground">No recent activity.</div>
                 ) : (
                    <div className="space-y-8">
                        {activities.map((activity) => (
                            <div key={activity.id} className="flex">
                                <div className="relative mr-4">
                                    <Avatar>
                                        <AvatarFallback>
                                            {activity.userIdentifier === "System" ? "AI" :
                                             activity.userIdentifier ? activity.userIdentifier.substring(0, 2).toUpperCase() : "???"}
                                        </AvatarFallback>
                                    </Avatar>
                                    <div className="absolute -bottom-0.5 -right-0.5 rounded-full bg-background p-0.5">
                                        <div className="rounded-full bg-background">{getActionIcon(activity.eventType)}</div>
                                    </div>
                                </div>
                                <div className="flex-1 space-y-1">
                                    <div className="text-sm">
                                        <span className="font-medium">{activity.userIdentifier || 'Unknown User'}</span>
                                        &nbsp; {/* Add space */}
                                        {getActionText(activity)}
                                    </div>
                                    <div className="text-xs text-muted-foreground flex items-center gap-2">
                                        <span>
                                            {activity.timestamp ? formatDistanceToNow(new Date(activity.timestamp), { addSuffix: true }) : 'N/A'}
                                        </span>
                                        {activity.relatedJobId && (
                                            <>
                                                <span>•</span>
                                                <Link to={`/jobs/${activity.relatedJobId}`} className="hover:underline">
                                                    Job #{activity.relatedJobId}
                                                </Link>
                                            </>
                                        )}
                                    </div>
                                </div>
                            </div>
                        ))}
                    </div>
                 )}
            </CardContent>
        </Card>
    )
}
