"use client"
import { useOnboarding } from './onboarding-context'

export function HighlightOverlay() {
  const { currentStep, isCompleted } = useOnboarding()
  const steps = [
    { id: 'dashboard-header' },
    { id: 'stats-cards' },
    { id: 'verification-chart' },
    { id: 'recent-activity' },
  ]

  if (isCompleted || currentStep >= steps.length) return null

  return (
    <div className="fixed inset-0 z-40 pointer-events-none">
      <div className="absolute inset-0 bg-black/30" />
      <div
        className="absolute border-2 border-primary rounded-lg shadow-lg transition-all duration-300"
        style={{
          boxShadow: '0 0 0 9999px rgba(0,0,0,0.5)',
          zIndex: 50,
          ...getElementPosition(steps[currentStep].id)
        }}
      />
    </div>
  )
}

function getElementPosition(id: string) {
  const element = document.getElementById(id)
  if (!element) return { display: 'none' }

  const rect = element.getBoundingClientRect()
  return {
    top: `${rect.top}px`,
    left: `${rect.left}px`,
    width: `${rect.width}px`,
    height: `${rect.height}px`,
  }
}