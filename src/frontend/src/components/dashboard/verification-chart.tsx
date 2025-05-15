
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "../../components/ui/card";
import { useState, useEffect } from "react"; // Import hooks
import { getDailyVerificationStats } from "../../lib/api"; // Import API function
import { Skeleton } from "../ui/skeleton"; // Import Skeleton
import { AlertTriangle } from "lucide-react"; // Import icon
import {
    BarChart,
    Bar,
    XAxis,
    YAxis,
    CartesianGrid,
    Tooltip,
    ResponsiveContainer,
    Legend,
} from "recharts";

// Interface matching backend DTO and api.ts
interface DailyVerificationStat {
  date: string;
  verified: number;
  flagged: number;
  pendingOrError: number;
}

export function VerificationChart() {
    const [chartData, setChartData] = useState<DailyVerificationStat[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        const fetchData = async () => {
            setIsLoading(true);
            setError(null);
            try {
                const data = await getDailyVerificationStats();
                setChartData(data);
            } catch (err) {
                setError("Failed to load chart data.");
                console.error(err);
            } finally {
                setIsLoading(false);
            }
        };
        fetchData();
    }, []);


    return (
        <Card>
            <CardHeader>
                <CardTitle>Verification Status</CardTitle>
                <CardDescription>Daily breakdown of job verification results (Last 7 Days)</CardDescription>
            </CardHeader>
            <CardContent>
                <div className="h-[300px]">
                 {isLoading ? (
                    <div className="flex items-center justify-center h-full">
                       <Skeleton className="h-full w-full" />
                    </div>
                 ) : error ? (
                    <div className="flex items-center justify-center h-full text-red-500">
                       <AlertTriangle className="mr-2 h-4 w-4" /> {error}
                    </div>
                 ) : (
                    <ResponsiveContainer width="100%" height="100%">
                        <BarChart data={chartData}>
                            <CartesianGrid strokeDasharray="3 3" />
                            <XAxis dataKey="date" /> {/* Use 'date' key from DTO */}
                            <YAxis />
                            <Tooltip />
                            <Legend />
                            <Bar dataKey="verified" name="Verified" fill="#22c55e" stackId="a" /> {/* Added stackId */}
                            <Bar dataKey="flagged" name="Flagged" fill="#ef4444" stackId="a" /> {/* Added stackId */}
                            <Bar dataKey="pendingOrError" name="Pending/Error" fill="#f59e0b" stackId="a" /> {/* Updated key and name */}
                        </BarChart>
                    </ResponsiveContainer>
                 )}
                </div>
            </CardContent>
        </Card>
    )
}
