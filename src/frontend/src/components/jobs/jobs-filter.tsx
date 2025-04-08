"use client"

import { Button } from "../../components/ui/button"
import { Input } from "../../components/ui/input"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "../../components/ui/select"
import { DatePickerWithRange } from "../../components/date-range-picker"
import { Search, X } from "lucide-react"

export function JobsFilter() {
  return (
    <div className="rounded-lg border p-4">
      <div className="flex flex-col gap-4 md:flex-row md:items-end">
        <div className="flex-1 space-y-2">
          <label htmlFor="search" className="text-sm font-medium">
            Search
          </label>
          <div className="relative">
            <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground" />
            <Input
              id="search"
              type="search"
              placeholder="Search by job ID, title, or customer..."
              className="w-full pl-8"
            />
          </div>
        </div>

        <div className="space-y-2">
          <label htmlFor="status" className="text-sm font-medium">
            Status
          </label>
          <Select defaultValue="all">
            <SelectTrigger id="status" className="w-[180px]">
              <SelectValue placeholder="Select status" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">All Statuses</SelectItem>
              <SelectItem value="verified">Verified</SelectItem>
              <SelectItem value="flagged">Flagged</SelectItem>
              <SelectItem value="pending">Pending</SelectItem>
            </SelectContent>
          </Select>
        </div>

        <div className="space-y-2">
          <label htmlFor="date" className="text-sm font-medium">
            Date Range
          </label>
          <DatePickerWithRange />
        </div>

        <div className="flex gap-2">
          <Button>Apply Filters</Button>
          <Button variant="outline" size="icon">
            <X className="h-4 w-4" />
            <span className="sr-only">Clear filters</span>
          </Button>
        </div>
      </div>
    </div>
  )
}

