"use client"

import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "../../components/ui/card"
import { Button } from "../../components/ui/button"
import { Switch } from "../../components/ui/switch"
import { Label } from "../../components/ui/label"
import { useState } from "react"

export function NotificationSettings() {
  const [settings, setSettings] = useState({
    emailAlerts: true,
    smsAlerts: false,
    inAppNotifications: true,
    dailyDigest: false,
    flaggedJobsOnly: true,
  })

  const handleToggle = (key: keyof typeof settings) => {
    setSettings({
      ...settings,
      [key]: !settings[key],
    })
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Notification Settings</CardTitle>
        <CardDescription>Configure how you receive notifications</CardDescription>
      </CardHeader>
      <CardContent>
        <div className="space-y-6">
          <div className="flex items-center justify-between">
            <div className="space-y-0.5">
              <Label htmlFor="emailAlerts">Email Alerts</Label>
              <div className="text-sm text-muted-foreground">Receive notifications via email</div>
            </div>
            <Switch
              id="emailAlerts"
              checked={settings.emailAlerts}
              onCheckedChange={() => handleToggle("emailAlerts")}
            />
          </div>

          <div className="flex items-center justify-between">
            <div className="space-y-0.5">
              <Label htmlFor="smsAlerts">SMS Alerts</Label>
              <div className="text-sm text-muted-foreground">Receive notifications via SMS</div>
            </div>
            <Switch id="smsAlerts" checked={settings.smsAlerts} onCheckedChange={() => handleToggle("smsAlerts")} />
          </div>

          <div className="flex items-center justify-between">
            <div className="space-y-0.5">
              <Label htmlFor="inAppNotifications">In-App Notifications</Label>
              <div className="text-sm text-muted-foreground">Receive notifications within the application</div>
            </div>
            <Switch
              id="inAppNotifications"
              checked={settings.inAppNotifications}
              onCheckedChange={() => handleToggle("inAppNotifications")}
            />
          </div>

          <div className="flex items-center justify-between">
            <div className="space-y-0.5">
              <Label htmlFor="dailyDigest">Daily Digest</Label>
              <div className="text-sm text-muted-foreground">Receive a daily summary of all activities</div>
            </div>
            <Switch
              id="dailyDigest"
              checked={settings.dailyDigest}
              onCheckedChange={() => handleToggle("dailyDigest")}
            />
          </div>

          <div className="flex items-center justify-between">
            <div className="space-y-0.5">
              <Label htmlFor="flaggedJobsOnly">Flagged Jobs Only</Label>
              <div className="text-sm text-muted-foreground">Only notify for jobs that require attention</div>
            </div>
            <Switch
              id="flaggedJobsOnly"
              checked={settings.flaggedJobsOnly}
              onCheckedChange={() => handleToggle("flaggedJobsOnly")}
            />
          </div>

          <Button className="w-full">Save Notification Settings</Button>
        </div>
      </CardContent>
    </Card>
  )
}

