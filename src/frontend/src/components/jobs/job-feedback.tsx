"use client"

import { Card, CardContent, CardHeader, CardTitle } from "../../components/ui/card"
import { Button } from "../../components/ui/button"
import { Textarea } from "../../components/ui/textarea"
import { RadioGroup, RadioGroupItem } from "../../components/ui/radio-group"
import { Label } from "../../components/ui/label"
import { useState } from "react"
import { CheckCircle, ThumbsDown, ThumbsUp } from "lucide-react"

interface JobFeedbackProps {
  jobId: string
}

export function JobFeedback({ jobId }: JobFeedbackProps) {
  const [feedbackType, setFeedbackType] = useState<string>("accurate")
  const [comments, setComments] = useState<string>("")
  const [submitted, setSubmitted] = useState<boolean>(false)

  const handleSubmit = () => {
    // This would normally send the feedback to the server
    console.log({ jobId, feedbackType, comments })
    setSubmitted(true)
  }

  if (submitted) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>Feedback</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="flex flex-col items-center justify-center py-6 text-center">
            <div className="rounded-full bg-green-100 p-3 dark:bg-green-900">
              <CheckCircle className="h-6 w-6 text-green-600 dark:text-green-400" />
            </div>
            <h3 className="mt-4 text-lg font-medium">Thank you for your feedback!</h3>
            <p className="mt-2 text-sm text-muted-foreground">
              Your input helps us improve our AI verification system.
            </p>
            <Button className="mt-4" variant="outline" onClick={() => setSubmitted(false)}>
              Provide Additional Feedback
            </Button>
          </div>
        </CardContent>
      </Card>
    )
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Provide Feedback</CardTitle>
      </CardHeader>
      <CardContent>
        <div className="space-y-6">
          <div>
            <div className="text-sm font-medium mb-3">Was the AI verification accurate?</div>
            <RadioGroup value={feedbackType} onValueChange={setFeedbackType} className="flex flex-col space-y-3">
              <div className="flex items-center space-x-2">
                <RadioGroupItem value="accurate" id="accurate" />
                <Label htmlFor="accurate" className="flex items-center">
                  <ThumbsUp className="mr-2 h-4 w-4 text-green-500" />
                  Yes, the verification was accurate
                </Label>
              </div>
              <div className="flex items-center space-x-2">
                <RadioGroupItem value="inaccurate" id="inaccurate" />
                <Label htmlFor="inaccurate" className="flex items-center">
                  <ThumbsDown className="mr-2 h-4 w-4 text-red-500" />
                  No, there were issues with the verification
                </Label>
              </div>
            </RadioGroup>
          </div>

          <div>
            <div className="text-sm font-medium mb-3">Additional Comments</div>
            <Textarea
              placeholder="Please provide any additional feedback or details about the verification..."
              value={comments}
              onChange={(e) => setComments(e.target.value)}
              rows={4}
            />
          </div>

          <Button onClick={handleSubmit}>Submit Feedback</Button>
        </div>
      </CardContent>
    </Card>
  )
}

