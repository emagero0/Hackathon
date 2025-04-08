"use client"

import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "../../components/ui/card"
import { Button } from "../../components/ui/button"
import { Input } from "../../components/ui/input"
import { Label } from "../../components/ui/label"
import { Switch } from "../../components/ui/switch"
import { useState } from "react"

export function IntegrationSettings() {
  const [settings, setSettings] = useState({
    dynamicsUrl: "https://dynamics365.example.com",
    apiKey: "••••••••••••••••",
    autoSync: true,
    syncInterval: "15",
  })

  const handleChange = (key: keyof typeof settings, value: string) => {
    setSettings({
      ...settings,
      [key]: value,
    })
  }

  const handleToggle = (key: keyof typeof settings) => {
    setSettings({
      ...settings,
      [key]: !settings[key],
    })
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Microsoft Dynamics 365 Integration</CardTitle>
        <CardDescription>Configure your ERP integration settings</CardDescription>
      </CardHeader>
      <CardContent>
        <div className="space-y-6">
          <div className="space-y-2">
            <Label htmlFor="dynamicsUrl">Dynamics 365 URL</Label>
            <Input
              id="dynamicsUrl"
              value={settings.dynamicsUrl}
              onChange={(e) => handleChange("dynamicsUrl", e.target.value)}
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor="apiKey">API Key</Label>
            <Input
              id="apiKey"
              type="password"
              value={settings.apiKey}
              onChange={(e) => handleChange("apiKey", e.target.value)}
            />
          </div>

          <div className="flex items-center justify-between">
            <div className="space-y-0.5">
              <Label htmlFor="autoSync">Automatic Synchronization</Label>
              <div className="text-sm text-muted-foreground">Automatically sync with Dynamics 365</div>
            </div>
            <Switch id="autoSync" checked={settings.autoSync} onCheckedChange={() => handleToggle("autoSync")} />
          </div>

          <div className="space-y-2">
            <Label htmlFor="syncInterval">Sync Interval (minutes)</Label>
            <Input
              id="syncInterval"
              type="number"
              value={settings.syncInterval}
              onChange={(e) => handleChange("syncInterval", e.target.value)}
              disabled={!settings.autoSync}
            />
          </div>

          <div className="flex gap-2">
            <Button className="flex-1">Save Integration Settings</Button>
            <Button variant="outline">Test Connection</Button>
          </div>
        </div>
      </CardContent>
    </Card>
  )
}

