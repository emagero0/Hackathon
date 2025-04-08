"use client"
import { createContext, useState, useContext } from 'react'

type OnboardingContextType = {
  currentStep: number
  isCompleted: boolean
  nextStep: () => void
  prevStep: () => void
  skipAll: () => void
}

const OnboardingContext = createContext<OnboardingContextType | null>(null)

export function OnboardingProvider({ children }: { children: React.ReactNode }) {
  const [currentStep, setCurrentStep] = useState(0)
  const [isCompleted, setIsCompleted] = useState(
    localStorage.getItem('onboardingCompleted') === 'true'
  )

  const TOTAL_STEPS = 4

  const nextStep = () => {
    if (currentStep < TOTAL_STEPS - 1) {
      setCurrentStep(currentStep + 1)
    } else {
      completeOnboarding()
    }
  }

  const prevStep = () => {
    if (currentStep > 0) setCurrentStep(currentStep - 1)
  }

  const skipAll = () => completeOnboarding()

  const completeOnboarding = () => {
    localStorage.setItem('onboardingCompleted', 'true')
    setIsCompleted(true)
  }

  return (
    <OnboardingContext.Provider
      value={{ currentStep, isCompleted, nextStep, prevStep, skipAll }}
    >
      {children}
    </OnboardingContext.Provider>
  )
}

export function useOnboarding() {
  const context = useContext(OnboardingContext)
  if (!context) {
    throw new Error('useOnboarding must be used within an OnboardingProvider')
  }
  return context
}