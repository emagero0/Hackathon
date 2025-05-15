"use client"

import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "../../components/ui/card";
import { Progress } from "../../components/ui/progress";
import { useState, useEffect } from "react"; // Import hooks
import { getDashboardStats } from "../../lib/api"; // Import API function
import { Skeleton } from "../ui/skeleton"; // Import Skeleton
import { AlertTriangle, CheckCircle, XCircle } from "lucide-react"; // Import icons

// Interface for the stats data fetched from the backend
interface DashboardStats {
  totalJobs: number;
  verifiedJobs: number;
  flaggedJobs: number;
  pendingJobs: number;
  errorJobs: number;
  verifiedPercentage: number;
  flaggedPercentage: number;
  pendingPercentage: number;
  errorPercentage: number;
}

export function PerformanceMetrics() {
  const [stats, setStats] = useState<DashboardStats | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const fetchStats = async () => {
      setIsLoading(true);
      setError(null);
      try {
        const data = await getDashboardStats();
        setStats(data);
      } catch (err) {
        setError("Failed to load performance metrics.");
        console.error(err);
      } finally {
        setIsLoading(false);
      }
    };
    fetchStats();
  }, []);

  // Hardcoded metrics (can be kept or removed)
  /*
  const _otherMetrics = [ // Renamed to avoid linting errors if not used, keep for reference
    // {
    //   name: "Accuracy Rate",
    //   value: 92,
    //   description: "Percentage of correctly verified documents",
    //   trend: "up",
    //   previousValue: 89,
    // },
    {
      name: "Average Processing Time",
      value: 42,
      unit: "seconds",
      description: "Average time to process a document",
      trend: "down",
      previousValue: 48,
    },
    {
      name: "Average Processing Time", // Duplicate, can be removed
      value: 42,
      unit: "seconds",
      description: "Average time to process a document",
      trend: "down",
      previousValue: 48,
    },
    {
      name: "False Positive Rate",
      value: 3.5,
      unit: "%",
      description: "Incorrectly flagged as having issues",
      trend: "down",
      previousValue: 4.2,
    },
    {
      name: "False Negative Rate",
      value: 2.8,
      unit: "%",
      description: "Issues missed by the system",
      trend: "down",
      previousValue: 3.1,
    },
    {
      name: "User Satisfaction",
      value: 88,
      unit: "%",
      description: "Based on user feedback",
      trend: "up",
      previousValue: 85,
    },
  ];
  */

  const renderStat = (
    icon: React.ReactNode,
    name: string,
    value: number,
    total: number,
    percentage: number,
    description: string,
    progressColorClass: string = "bg-primary" // Default progress color
  ) => (
    <div className="space-y-2">
      <div className="flex items-center justify-between">
        <div className="flex items-center">
           {icon}
          <div className="ml-2">
            <div className="font-medium">{name}</div>
            <div className="text-xs text-muted-foreground">{description}</div>
          </div>
        </div>
        <div className="text-right">
          <div className="text-2xl font-bold">{value}</div>
          <div className="text-xs text-muted-foreground">
            {percentage.toFixed(1)}% of {total}
          </div>
        </div>
      </div>
      <Progress value={percentage} className={`h-2 [&>*]:${progressColorClass}`} />
    </div>
  );


  return (
    <Card>
      <CardHeader>
        <CardTitle>Performance Metrics</CardTitle>
        <CardDescription>Key performance indicators for the verification system</CardDescription>
      </CardHeader>
      <CardContent>
        {isLoading ? (
          <div className="space-y-6">
            <Skeleton className="h-16 w-full" />
            <Skeleton className="h-16 w-full" />
            <Skeleton className="h-16 w-full" />
          </div>
        ) : error ? (
          <div className="text-red-500">{error}</div>
        ) : stats ? (
          <div className="space-y-6">
            {/* Display fetched stats */}
             {renderStat(
               <CheckCircle className="h-5 w-5 text-green-500" />,
               "Verified Jobs",
               stats.verifiedJobs,
               stats.totalJobs,
               stats.verifiedPercentage,
               "Jobs successfully verified without discrepancies.",
               "bg-green-500"
             )}
             {renderStat(
                <AlertTriangle className="h-5 w-5 text-yellow-500" />,
                "Flagged Jobs",
                stats.flaggedJobs,
                stats.totalJobs,
                stats.flaggedPercentage,
                "Jobs flagged due to discrepancies found.",
                "bg-yellow-500"
             )}
             {renderStat(
                <XCircle className="h-5 w-5 text-red-500" />,
                "Error Jobs",
                stats.errorJobs,
                stats.totalJobs,
                stats.errorPercentage,
                "Jobs that failed during the verification process.",
                "bg-red-500"
             )}

            {/* Display other hardcoded metrics if desired */}
            {/* {otherMetrics.map((metric, index) => ( ... existing map logic ... ))} */}

          </div>
        ) : (
          <div>No data available.</div>
        )}
      </CardContent>
    </Card>
  )
}
