import { OnboardingProvider } from '../components/onboarding/onboarding-context'
import { HighlightOverlay } from '../components/onboarding/highlight-overlay'
import { OnboardingGuide } from '../components/onboarding/onboarding-guide'
import { DashboardHeader } from '../components/dashboard/dashboard-header'
import { DashboardStats } from '../components/dashboard/dashboard-stats'
import { VerificationChart } from '../components/dashboard/verification-chart'
import { RecentActivity } from '../components/dashboard/recent-activity'
import { JobsOverview } from "../components/dashboard/jobs-overview"

export default function Dashboard() {
  return (
    <OnboardingProvider>
      <main className="flex min-h-screen flex-col p-6">
        <DashboardHeader />
        <div className="grid gap-6 md:grid-cols-2 lg:grid-cols-4 mt-6">
          <DashboardStats />
        </div>
        <div className="grid gap-6 md:grid-cols-2 lg:grid-cols-2 mt-6">
          <div className="lg:col-span-1">
          <JobsOverview />
          </div>
          <div>
            <VerificationChart />
          </div>
        </div>
        <div className="mt-6">
          <RecentActivity />
        </div>
        
        <OnboardingGuide />
        <HighlightOverlay />
      </main>
    </OnboardingProvider>
  )
}