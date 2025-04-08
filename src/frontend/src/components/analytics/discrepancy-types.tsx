"use client"

import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "../../components/ui/card"
import { PieChart, Pie, Cell, ResponsiveContainer, Legend, Tooltip } from "recharts"

export function DiscrepancyTypes() {
  const data = [
    { name: "Amount Mismatch", value: 35 },
    { name: "Missing Signature", value: 25 },
    { name: "Incorrect Tax", value: 15 },
    { name: "Wrong Date", value: 10 },
    { name: "Quantity Mismatch", value: 8 },
    { name: "Other", value: 7 },
  ]

  const COLORS = ["#ef4444", "#f59e0b", "#3b82f6", "#8b5cf6", "#ec4899", "#6b7280"]

  return (
    <Card>
      <CardHeader>
        <CardTitle>Discrepancy Types</CardTitle>
        <CardDescription>Breakdown of flagged issues by category</CardDescription>
      </CardHeader>
      <CardContent>
        <div className="h-[300px]">
          <ResponsiveContainer width="100%" height="100%">
            <PieChart>
              <Pie
                data={data}
                cx="50%"
                cy="50%"
                labelLine={false}
                outerRadius={80}
                fill="#8884d8"
                dataKey="value"
                label={({ name, percent }) => `${name} ${(percent * 100).toFixed(0)}%`}
              >
                {data.map((_entry, index) => (
                  <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
                ))}
              </Pie>
              <Tooltip formatter={(value) => [`${value} issues`, "Count"]} />
              <Legend />
            </PieChart>
          </ResponsiveContainer>
        </div>
      </CardContent>
    </Card>
  )
}

