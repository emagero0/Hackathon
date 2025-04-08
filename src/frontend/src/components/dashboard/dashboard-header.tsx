"use client"
import { Button } from "../../components/ui/button"
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from "../../components/ui/dropdown-menu"
import { ChevronDown, Download, RefreshCw } from "lucide-react"
import { DatePickerWithRange } from "../../components/date-range-picker"
import { useState } from "react"

export function DashboardHeader() {
    const [isRefreshing, setIsRefreshing] = useState(false)

    const handleRefresh = () => {
        setIsRefreshing(true)
        setTimeout(() => setIsRefreshing(false), 1000)
    }

    return (
        <div id="dashboard-header" className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between md:mt-0 mt-8">

            <div>
                <h1 className="text-3xl font-bold tracking-tight">Dashboard</h1>
                <p className="text-muted-foreground">Monitor job verification status and performance metrics</p>
            </div>
            <div className="flex flex-col gap-2 sm:flex-row sm:items-center">
                <DatePickerWithRange />
                <div className="flex gap-2">
                    <Button variant="outline" size="sm" onClick={handleRefresh}>
                        <RefreshCw className={`mr-2 h-4 w-4 ${isRefreshing ? "animate-spin" : ""}`} />
                        Refresh
                    </Button>
                    <DropdownMenu>
                        <DropdownMenuTrigger asChild>
                            <Button variant="outline" size="sm">
                                <Download className="mr-2 h-4 w-4" />
                                Export
                                <ChevronDown className="ml-2 h-4 w-4" />
                            </Button>
                        </DropdownMenuTrigger>
                        <DropdownMenuContent align="end">
                            <DropdownMenuItem>Export as CSV</DropdownMenuItem>
                            <DropdownMenuItem>Export as PDF</DropdownMenuItem>
                            <DropdownMenuItem>Export as Excel</DropdownMenuItem>
                        </DropdownMenuContent>
                    </DropdownMenu>
                </div>
            </div>
        </div>
    )
}

