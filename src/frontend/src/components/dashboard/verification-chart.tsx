
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "../../components/ui/card"
import {
    BarChart,
    Bar,
    XAxis,
    YAxis,
    CartesianGrid,
    Tooltip,
    ResponsiveContainer,
    Legend,
} from "recharts"

export function VerificationChart() {
    const data = [
        { name: "Mon", verified: 65, flagged: 12, pending: 23 },
        { name: "Tue", verified: 59, flagged: 15, pending: 26 },
        { name: "Wed", verified: 80, flagged: 8, pending: 12 },
        { name: "Thu", verified: 81, flagged: 10, pending: 9 },
        { name: "Fri", verified: 56, flagged: 18, pending: 26 },
        { name: "Sat", verified: 40, flagged: 5, pending: 15 },
        { name: "Sun", verified: 30, flagged: 3, pending: 7 },
    ]

    return (
        <Card>
            <CardHeader>
                <CardTitle>Verification Status</CardTitle>
                <CardDescription>Daily breakdown of job verification results</CardDescription>
            </CardHeader>
            <CardContent>
                <div className="h-[300px]">
                    <ResponsiveContainer width="100%" height="100%">
                        <BarChart data={data}>
                            <CartesianGrid strokeDasharray="3 3" />
                            <XAxis dataKey="name" />
                            <YAxis />
                            <Tooltip />
                            <Legend />
                            <Bar dataKey="verified" name="Verified" fill="#22c55e" />
                            <Bar dataKey="flagged" name="Flagged" fill="#ef4444" />
                            <Bar dataKey="pending" name="Pending" fill="#f59e0b" />
                        </BarChart>
                    </ResponsiveContainer>
                </div>
            </CardContent>
        </Card>
    )
}

