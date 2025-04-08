"use client"

import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "../../components/ui/card"
import { Button } from "../../components/ui/button"
import { Input } from "../../components/ui/input"
import { Label } from "../../components/ui/label"
import { Avatar, AvatarFallback, AvatarImage } from "../../components/ui/avatar"
import { useState } from "react"

export function UserSettings() {
  const [user, setUser] = useState({
    name: "John Doe",
    email: "john.doe@example.com",
    role: "Verification Manager",
    department: "Operations",
    avatar: "/placeholder.svg?height=100&width=100",
  })

  const handleChange = (key: keyof typeof user, value: string) => {
    setUser({
      ...user,
      [key]: value,
    })
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>User Profile</CardTitle>
        <CardDescription>Manage your account information</CardDescription>
      </CardHeader>
      <CardContent>
        <div className="space-y-6">
          <div className="flex flex-col items-center gap-4 sm:flex-row">
            <Avatar className="h-20 w-20">
              <AvatarImage src={user.avatar} alt={user.name} />
              <AvatarFallback>JD</AvatarFallback>
            </Avatar>
            <div className="flex-1 space-y-2 text-center sm:text-left">
              <div className="space-y-0.5">
                <h3 className="text-lg font-semibold">{user.name}</h3>
                <p className="text-sm text-muted-foreground">{user.role}</p>
              </div>
              <Button variant="outline" size="sm">
                Change Avatar
              </Button>
            </div>
          </div>

          <div className="space-y-2">
            <Label htmlFor="name">Full Name</Label>
            <Input id="name" value={user.name} onChange={(e) => handleChange("name", e.target.value)} />
          </div>

          <div className="space-y-2">
            <Label htmlFor="email">Email Address</Label>
            <Input id="email" type="email" value={user.email} onChange={(e) => handleChange("email", e.target.value)} />
          </div>

          <div className="space-y-2">
            <Label htmlFor="role">Role</Label>
            <Input id="role" value={user.role} onChange={(e) => handleChange("role", e.target.value)} disabled />
          </div>

          <div className="space-y-2">
            <Label htmlFor="department">Department</Label>
            <Input
              id="department"
              value={user.department}
              onChange={(e) => handleChange("department", e.target.value)}
              disabled
            />
          </div>

          <div className="flex gap-2">
            <Button className="flex-1">Save Profile</Button>
            <Button variant="outline">Change Password</Button>
          </div>
        </div>
      </CardContent>
    </Card>
  )
}

