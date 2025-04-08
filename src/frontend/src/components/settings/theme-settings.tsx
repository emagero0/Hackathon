"use client"

import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "../../components/ui/card"
import { Button } from "../../components/ui/button"
import { useTheme } from "next-themes"
import { Moon, Sun, Monitor } from "lucide-react"

export function ThemeSettings() {
  const { theme, setTheme } = useTheme()

  return (
    <Card>
      <CardHeader>
        <CardTitle>Theme Settings</CardTitle>
        <CardDescription>Customize the appearance of the application</CardDescription>
      </CardHeader>
      <CardContent>
        <div className="space-y-6">
          <div className="grid grid-cols-3 gap-3">
            <Button
              variant={theme === "light" ? "default" : "outline"}
              className="flex flex-col items-center justify-center gap-2 p-4"
              onClick={() => setTheme("light")}
            >
              <Sun className="h-6 w-6" />
              <span>Light</span>
            </Button>
            <Button
              variant={theme === "dark" ? "default" : "outline"}
              className="flex flex-col items-center justify-center gap-2 p-4"
              onClick={() => setTheme("dark")}
            >
              <Moon className="h-6 w-6" />
              <span>Dark</span>
            </Button>
            <Button
              variant={theme === "system" ? "default" : "outline"}
              className="flex flex-col items-center justify-center gap-2 p-4"
              onClick={() => setTheme("system")}
            >
              <Monitor className="h-6 w-6" />
              <span>System</span>
            </Button>
          </div>

          <Button className="w-full">Save Theme Settings</Button>
        </div>
      </CardContent>
    </Card>
  )
}

