"use client"
import { useOnboarding } from './onboarding-context'
import { Button } from '../ui/button'
import { X, ArrowLeft, ArrowRight } from 'lucide-react'

export function OnboardingGuide() {
  const { currentStep, isCompleted, nextStep, prevStep, skipAll } = useOnboarding()

  if (isCompleted) return null

  const steps = [
    {
      target: '#dashboard-header',
      title: 'Welcome to Your Dashboard',
      content: 'This is your command center for monitoring job verification status and metrics.',
    },
    {
      target: '#stats-cards',
      title: 'Key Metrics at a Glance',
      content: 'Track verified, flagged, and pending jobs with these real-time statistics.',
    },
    {
      target: '#verification-chart',
      title: 'Visual Trends',
      content: 'See daily verification patterns to identify workflow bottlenecks.',
    },
    {
      target: '#recent-activity',
      title: 'Activity Stream',
      content: 'Monitor recent actions by your team and the verification system.',
    },
  ]

  const currentGuide = steps[currentStep]

  return (
    <div className="fixed inset-0 z-50 bg-black/50 backdrop-blur-sm flex items-center justify-center">
      <div 
        className="relative bg-background p-6 rounded-lg max-w-md border"
        style={{
          position: 'absolute',
          top: '50%',
          left: '50%',
          transform: 'translate(-50%, -50%)'
        }}
      >
        <Button
          variant="ghost"
          size="icon"
          className="absolute right-2 top-2"
          onClick={skipAll}
        >
          <X className="h-4 w-4" />
          <span className="sr-only">Close</span>
        </Button>

        <h3 className="font-bold text-lg mb-2">{currentGuide.title}</h3>
        <p className="mb-6 text-muted-foreground">{currentGuide.content}</p>

        <div className="flex justify-between items-center">
          <div className="text-sm text-muted-foreground">
            Step {currentStep + 1} of {steps.length}
          </div>

          <div className="flex gap-2">
            {currentStep > 0 && (
              <Button variant="outline" onClick={prevStep}>
                <ArrowLeft className="mr-2 h-4 w-4" />
                Back
              </Button>
            )}
            <Button onClick={nextStep}>
              {currentStep === steps.length - 1 ? 'Get Started' : 'Next'}
              <ArrowRight className="ml-2 h-4 w-4" />
            </Button>
          </div>
        </div>
      </div>
    </div>
  )
}