"use client"

import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "../../components/ui/card"
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Legend } from "recharts"

export function FeedbackAnalysis() {
  const data = [
    { name: "Week 1", positive: 42, negative: 8 },
    { name: "Week 2", positive: 38, negative: 12 },
    { name: "Week 3", positive: 45, negative: 5 },
    { name: "Week 4", positive: 40, negative: 10 },
  ]

  return (
    <Card>
      <CardHeader>
        <CardTitle>User Feedback</CardTitle>
        <CardDescription>Analysis of user feedback on verification accuracy</CardDescription>
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
              <Bar dataKey="positive" name="Positive Feedback" fill="#22c55e" />
              <Bar dataKey="negative" name="Negative Feedback" fill="#ef4444" />
            </BarChart>
          </ResponsiveContainer>
        </div>
      </CardContent>
    </Card>
  )
}

