import { useState, useEffect } from "react"
import { Card, CardContent, CardHeader, CardTitle } from "../../components/ui/card"
import { AlertTriangle, CheckCircle, Clock, FileText } from "lucide-react"
import { getDashboardStats } from "../../lib/api"

export function DashboardStats() {
    const [stats, setStats] = useState([
        {
            title: "Total Jobs",
            value: "Loading...",
            icon: FileText,
            description: "",
            trend: "neutral",
        },
        {
            title: "Verified",
            value: "Loading...",
            icon: CheckCircle,
            description: "",
            trend: "neutral",
        },
        {
            title: "Flagged",
            value: "Loading...",
            icon: AlertTriangle,
            description: "",
            trend: "neutral",
        },
        {
            title: "Pending",
            value: "Loading...",
            icon: Clock,
            description: "",
            trend: "neutral",
        },
    ])

    useEffect(() => {
        const fetchStats = async () => {
            try {
                const data = await getDashboardStats();
                setStats([
                    {
                        title: "Total Jobs",
                        value: data.totalJobs.toString(),
                        icon: FileText,
                        description: "",
                        trend: "neutral",
                    },
                    {
                        title: "Verified",
                        value: data.verifiedJobs.toString(),
                        icon: CheckCircle,
                        description: `${data.verifiedPercentage.toFixed(1)}%`,
                        trend: "up",
                    },
                    {
                        title: "Flagged",
                        value: data.flaggedJobs.toString(),
                        icon: AlertTriangle,
                        description: `${data.flaggedPercentage.toFixed(1)}%`,
                        trend: "down",
                    },
                    {
                        title: "Pending",
                        value: data.pendingJobs.toString(),
                        icon: Clock,
                        description: `${data.pendingPercentage.toFixed(1)}%`,
                        trend: "neutral",
                    },
                ]);
            } catch (error) {
                console.error("Failed to fetch dashboard stats:", error);
                // Handle error appropriately (e.g., display an error message)
            }
        };

        fetchStats();
    }, []);

    return (
        <>
            {stats.map((stat, index) => (
                <Card key={index}>
                    <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                        <CardTitle className="text-sm font-medium">{stat.title}</CardTitle>
                        <stat.icon
                            className={`h-4 w-4 ${
                                stat.title === "Verified"
                                    ? "text-green-500"
                                    : stat.title === "Flagged"
                                        ? "text-red-500"
                                        : stat.title === "Pending"
                                            ? "text-amber-500"
                                            : "text-muted-foreground"
                            }`}
                        />
                    </CardHeader>
                    <CardContent>
                        <div className="text-2xl font-bold">{stat.value}</div>
                        <p
                            className={`text-xs ${
                                stat.trend === "up"
                                    ? "text-green-500"
                                    : stat.trend === "down"
                                        ? "text-red-500"
                                        : "text-muted-foreground"
                            }`}
                        >
                            {stat.description}
                        </p>
                    </CardContent>
                </Card>
            ))}
        </>
    )
}
