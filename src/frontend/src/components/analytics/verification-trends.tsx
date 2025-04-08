"use client"

import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "../../components/ui/card"
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  Legend,
} from "recharts"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "../../components/ui/tabs"

export function VerificationTrends() {
  const dailyData = [
    { name: "Apr 1", verified: 65, flagged: 12, pending: 23 },
    { name: "Apr 2", verified: 59, flagged: 15, pending: 26 },
    { name: "Apr 3", verified: 80, flagged: 8, pending: 12 },
    { name: "Apr 4", verified: 81, flagged: 10, pending: 9 },
    { name: "Apr 5", verified: 56, flagged: 18, pending: 26 },
    { name: "Apr 6", verified: 40, flagged: 5, pending: 15 },
    { name: "Apr 7", verified: 30, flagged: 3, pending: 7 },
  ]

  const weeklyData = [
    { name: "Week 1", verified: 350, flagged: 65, pending: 85 },
    { name: "Week 2", verified: 390, flagged: 72, pending: 78 },
    { name: "Week 3", verified: 420, flagged: 58, pending: 62 },
    { name: "Week 4", verified: 380, flagged: 70, pending: 90 },
  ]

  const monthlyData = [
    { name: "Jan", verified: 1200, flagged: 250, pending: 350 },
    { name: "Feb", verified: 1350, flagged: 220, pending: 330 },
    { name: "Mar", verified: 1450, flagged: 280, pending: 270 },
    { name: "Apr", verified: 1540, flagged: 265, pending: 315 },
  ]

  return (
    <Card>
      <CardHeader>
        <CardTitle>Verification Trends</CardTitle>
        <CardDescription>Track verification results over time</CardDescription>
      </CardHeader>
      <CardContent>
        <Tabs defaultValue="daily">
          <TabsList className="mb-4">
            <TabsTrigger value="daily">Daily</TabsTrigger>
            <TabsTrigger value="weekly">Weekly</TabsTrigger>
            <TabsTrigger value="monthly">Monthly</TabsTrigger>
          </TabsList>
          <TabsContent value="daily">
            <div className="h-[300px]">
              <ResponsiveContainer width="100%" height="100%">
                <LineChart data={dailyData}>
                  <CartesianGrid strokeDasharray="3 3" />
                  <XAxis dataKey="name" />
                  <YAxis />
                  <Tooltip />
                  <Legend />
                  <Line type="monotone" dataKey="verified" name="Verified" stroke="#22c55e" strokeWidth={2} />
                  <Line type="monotone" dataKey="flagged" name="Flagged" stroke="#ef4444" strokeWidth={2} />
                  <Line type="monotone" dataKey="pending" name="Pending" stroke="#f59e0b" strokeWidth={2} />
                </LineChart>
              </ResponsiveContainer>
            </div>
          </TabsContent>
          <TabsContent value="weekly">
            <div className="h-[300px]">
              <ResponsiveContainer width="100%" height="100%">
                <LineChart data={weeklyData}>
                  <CartesianGrid strokeDasharray="3 3" />
                  <XAxis dataKey="name" />
                  <YAxis />
                  <Tooltip />
                  <Legend />
                  <Line type="monotone" dataKey="verified" name="Verified" stroke="#22c55e" strokeWidth={2} />
                  <Line type="monotone" dataKey="flagged" name="Flagged" stroke="#ef4444" strokeWidth={2} />
                  <Line type="monotone" dataKey="pending" name="Pending" stroke="#f59e0b" strokeWidth={2} />
                </LineChart>
              </ResponsiveContainer>
            </div>
          </TabsContent>
          <TabsContent value="monthly">
            <div className="h-[300px]">
              <ResponsiveContainer width="100%" height="100%">
                <LineChart data={monthlyData}>
                  <CartesianGrid strokeDasharray="3 3" />
                  <XAxis dataKey="name" />
                  <YAxis />
                  <Tooltip />
                  <Legend />
                  <Line type="monotone" dataKey="verified" name="Verified" stroke="#22c55e" strokeWidth={2} />
                  <Line type="monotone" dataKey="flagged" name="Flagged" stroke="#ef4444" strokeWidth={2} />
                  <Line type="monotone" dataKey="pending" name="Pending" stroke="#f59e0b" strokeWidth={2} />
                </LineChart>
              </ResponsiveContainer>
            </div>
          </TabsContent>
        </Tabs>
      </CardContent>
    </Card>
  )
}

