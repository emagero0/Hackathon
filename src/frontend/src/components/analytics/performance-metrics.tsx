"use client"

import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "../../components/ui/card"
import { Progress } from "../../components/ui/progress"

export function PerformanceMetrics() {
  const metrics = [
    {
      name: "Accuracy Rate",
      value: 92,
      description: "Percentage of correctly verified documents",
      trend: "up",
      previousValue: 89,
    },
    {
      name: "Average Processing Time",
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
  ]

  return (
    <Card>
      <CardHeader>
        <CardTitle>Performance Metrics</CardTitle>
        <CardDescription>Key performance indicators for the verification system</CardDescription>
      </CardHeader>
      <CardContent>
        <div className="space-y-6">
          {metrics.map((metric, index) => (
            <div key={index} className="space-y-2">
              <div className="flex items-center justify-between">
                <div>
                  <div className="font-medium">{metric.name}</div>
                  <div className="text-xs text-muted-foreground">{metric.description}</div>
                </div>
                <div className="text-right">
                  <div className="text-2xl font-bold">
                    {metric.value}
                    {metric.unit}
                  </div>
                  <div
                    className={`text-xs ${
                      metric.trend === "up"
                        ? "text-green-500"
                        : metric.trend === "down" && (metric.name.includes("False") || metric.name.includes("Time"))
                          ? "text-green-500"
                          : metric.trend === "down"
                            ? "text-red-500"
                            : "text-muted-foreground"
                    }`}
                  >
                    {metric.trend === "up" ? "↑" : "↓"} from {metric.previousValue}
                    {metric.unit}
                  </div>
                </div>
              </div>
              <Progress
                value={metric.name.includes("False") ? 100 - metric.value * 10 : metric.value}
                className="h-2"
              />
            </div>
          ))}
        </div>
      </CardContent>
    </Card>
  )
}

