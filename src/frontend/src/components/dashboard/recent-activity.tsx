"use client"

import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "../../components/ui/card"
import { Avatar, AvatarFallback, AvatarImage } from "../../components/ui/avatar"
import { Badge } from "../../components/ui/badge"
import { AlertTriangle, CheckCircle, Clock, FileText, User } from "lucide-react"

export function RecentActivity() {
    const activities = [
        {
            id: 1,
            user: {
                name: "John Doe",
                avatar: "/placeholder.svg?height=40&width=40",
                initials: "JD",
            },
            action: "verified",
            jobId: "JOB-1234",
            jobTitle: "Sales Quote #SQ-5678",
            time: "2 hours ago",
        },
        {
            id: 2,
            user: {
                name: "Jane Smith",
                avatar: "/placeholder.svg?height=40&width=40",
                initials: "JS",
            },
            action: "flagged",
            jobId: "JOB-1235",
            jobTitle: "Invoice #INV-9012",
            time: "3 hours ago",
            issues: ["Amount mismatch", "Missing signature"],
        },
        {
            id: 3,
            user: {
                name: "System",
                avatar: "/placeholder.svg?height=40&width=40",
                initials: "AI",
            },
            action: "processing",
            jobId: "JOB-1236",
            jobTitle: "Delivery Note #DN-3456",
            time: "5 hours ago",
        },
        {
            id: 4,
            user: {
                name: "Mike Johnson",
                avatar: "/placeholder.svg?height=40&width=40",
                initials: "MJ",
            },
            action: "submitted",
            jobId: "JOB-1238",
            jobTitle: "Credit Note #CN-1122",
            time: "10 hours ago",
        },
        {
            id: 5,
            user: {
                name: "Sarah Williams",
                avatar: "/placeholder.svg?height=40&width=40",
                initials: "SW",
            },
            action: "feedback",
            jobId: "JOB-1235",
            jobTitle: "Invoice #INV-9012",
            time: "11 hours ago",
            feedback: "Confirmed discrepancy in amount calculation",
        },
    ]

    const getActionIcon = (action: string) => {
        switch (action) {
            case "verified":
                return <CheckCircle className="h-5 w-5 text-green-500" />
            case "flagged":
                return <AlertTriangle className="h-5 w-5 text-red-500" />
            case "processing":
                return <Clock className="h-5 w-5 text-amber-500" />
            case "submitted":
                return <FileText className="h-5 w-5 text-blue-500" />
            case "feedback":
                return <User className="h-5 w-5 text-purple-500" />
            default:
                return <FileText className="h-5 w-5" />
        }
    }

    const getActionText = (activity: any) => {
        switch (activity.action) {
            case "verified":
                return (
                    <span>
            verified <span className="font-medium">{activity.jobTitle}</span>
          </span>
                )
            case "flagged":
                return (
                    <span>
            flagged <span className="font-medium">{activity.jobTitle}</span> for review
          </span>
                )
            case "processing":
                return (
                    <span>
            is processing <span className="font-medium">{activity.jobTitle}</span>
          </span>
                )
            case "submitted":
                return (
                    <span>
            submitted <span className="font-medium">{activity.jobTitle}</span> for verification
          </span>
                )
            case "feedback":
                return (
                    <span>
            provided feedback on <span className="font-medium">{activity.jobTitle}</span>
          </span>
                )
            default:
                return <span>updated {activity.jobTitle}</span>
        }
    }

    return (
        <Card>
            <CardHeader>
                <CardTitle>Recent Activity</CardTitle>
                <CardDescription>Latest actions and updates from the verification system</CardDescription>
            </CardHeader>
            <CardContent>
                <div className="space-y-8">
                    {activities.map((activity) => (
                        <div key={activity.id} className="flex">
                            <div className="relative mr-4">
                                <Avatar>
                                    <AvatarImage src={activity.user.avatar} alt={activity.user.name} />
                                    <AvatarFallback>{activity.user.initials}</AvatarFallback>
                                </Avatar>
                                <div className="absolute -bottom-0.5 -right-0.5 rounded-full bg-background p-0.5">
                                    <div className="rounded-full bg-background">{getActionIcon(activity.action)}</div>
                                </div>
                            </div>
                            <div className="flex-1 space-y-1">
                                <div className="flex items-center gap-2">
                                    <span className="font-medium">{activity.user.name}</span>
                                    {getActionText(activity)}
                                </div>
                                <div className="text-sm text-muted-foreground flex items-center gap-2">
                                    <span>{activity.time}</span>
                                    <span>•</span>
                                    <span>{activity.jobId}</span>
                                </div>
                                {activity.issues && (
                                    <div className="mt-1 flex flex-wrap gap-1">
                                        {activity.issues.map((issue: string, i: number) => (
                                            <Badge key={i} variant="destructive" className="text-xs">
                                                {issue}
                                            </Badge>
                                        ))}
                                    </div>
                                )}
                                {activity.feedback && <div className="mt-1 text-sm italic">"{activity.feedback}"</div>}
                            </div>
                        </div>
                    ))}
                </div>
            </CardContent>
        </Card>
    )
}

