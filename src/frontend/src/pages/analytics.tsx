import { AnalyticsHeader } from "../components/analytics/analytics-header"
import { VerificationTrends } from "../components/analytics/verification-trends"
import { DiscrepancyTypes } from "../components/analytics/discrepancy-types"
import { FeedbackAnalysis } from "../components/analytics/feedback-analysis"
import { PerformanceMetrics } from "../components/analytics/performance-metrics"

export default function Analytics() {
  return (
    <main className="flex min-h-screen flex-col p-6">
      <AnalyticsHeader />

      <div className="grid gap-6 md:grid-cols-2 mt-6">
        <div>
          <VerificationTrends />
        </div>
        <div>
          <DiscrepancyTypes />
        </div>
      </div>

      <div className="grid gap-6 md:grid-cols-2 mt-6">
        <div>
          <FeedbackAnalysis />
        </div>
        <div>
          <PerformanceMetrics />
        </div>
      </div>
    </main>
  )
}

