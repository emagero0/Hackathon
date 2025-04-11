"use client"

import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "../../components/ui/card"
import { Avatar, AvatarFallback, AvatarImage } from "../../components/ui/avatar"
import { Badge } from "../../components/ui/badge" // Keep Badge if needed
import { AlertTriangle, CheckCircle, Clock, FileText, User, MessageSquare, UploadCloud, DatabaseZap, ServerCrash, Loader2 } from "lucide-react" // Added icons
import { useState, useEffect } from "react" // Added imports
import { getActivityLog } from "../../lib/api" // Added import
import { formatDistanceToNow } from 'date-fns' // Added import
import { Link } from "react-router-dom" // Added import

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

    useEffect(() => {
      const fetchActivities = async () => {
        setIsLoading(true);
        setError(null);
        try {
          // Fetch the first page of activities (e.g., 5 items)
          const data = await getActivityLog(0, 5); // Fetch page 0, size 5
          // Ensure data.content exists and is an array before setting state
          setActivities(data && Array.isArray(data.content) ? data.content : []);
        } catch (err) {
          setError("Failed to fetch recent activity.");
          console.error(err);
        } finally {
          setIsLoading(false);
        }
      };

      fetchActivities();
    }, []);

    // Map backend eventType to icons
    const getActionIcon = (eventType: string) => {
        switch (eventType) {
            case "JOB_PROCESSED":
            case "VERIFIED": // Assuming VERIFIED status might be logged
                 return <CheckCircle className="h-5 w-5 text-green-500" />
            case "FLAGGED": // Assuming FLAGGED status might be logged
            case "BC_UPDATE_FAILURE":
            case "ERROR":
                return <AlertTriangle className="h-5 w-5 text-red-500" />
            case "PROCESSING": // Assuming PROCESSING status might be logged
            case "NLP_STARTED":
            case "OCR_STARTED":
                return <Clock className="h-5 w-5 text-amber-500" />
            case "BC_UPDATE_SUCCESS":
                return <DatabaseZap className="h-5 w-5 text-blue-500" /> // Icon for DB update
            case "FEEDBACK_SUBMITTED":
                return <MessageSquare className="h-5 w-5 text-purple-500" /> // Icon for feedback
            case "OCR_COMPLETED":
            case "NLP_COMPLETED":
                 return <FileText className="h-5 w-5 text-gray-500" /> // Generic file/doc icon
            // Add more cases as needed based on ActivityLogService constants
            default:
                return <FileText className="h-5 w-5 text-muted-foreground" />
        }
    }

    // Display description directly from the log
    const getActionText = (activity: ActivityLogData) => {
        return <span>{activity.description}</span>;
    }

    return (
        <Card>
            <CardHeader>
                <CardTitle>Recent Activity</CardTitle>
                <CardDescription>Latest actions and updates from the verification system</CardDescription>
            </CardHeader>
            <CardContent>
                 {isLoading ? (
                    <div className="flex items-center justify-center py-8">
                        <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
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
                                    {/* Use generic user avatar or initials based on userIdentifier */}
                                    <Avatar>
                                        {/* <AvatarImage src={activity.user.avatar} alt={activity.user.name} /> */}
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
                                                {/* Link to the job detail page */}
                                                <Link to={`/jobs/${activity.relatedJobId}`} className="hover:underline">
                                                    Job #{activity.relatedJobId}
                                                </Link>
                                            </>
                                        )}
                                    </div>
                                    {/* Remove hardcoded issues/feedback display */}
                                </div>
                            </div>
                        ))}
                    </div>
                 )}
            </CardContent>
        </Card>
    )
}
