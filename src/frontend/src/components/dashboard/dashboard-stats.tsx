import { Card, CardContent, CardHeader, CardTitle } from "../../components/ui/card"
import { AlertTriangle, CheckCircle, Clock, FileText } from "lucide-react"

export function DashboardStats() {
    const stats = [
        {
            title: "Total Jobs",
            value: "1,284",
            icon: FileText,
            description: "+12% from last month",
            trend: "up",
        },
        {
            title: "Verified",
            value: "942",
            icon: CheckCircle,
            description: "73% success rate",
            trend: "up",
        },
        {
            title: "Flagged",
            value: "128",
            icon: AlertTriangle,
            description: "10% of total jobs",
            trend: "down",
        },
        {
            title: "Pending",
            value: "214",
            icon: Clock,
            description: "17% of total jobs",
            trend: "neutral",
        },
    ]

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

