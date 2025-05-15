import React from "react"; // Import React
import { cn } from "../../lib/utils" // Ensure this path is correct

function Skeleton({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) { // Use React.HTMLAttributes
  return (
    <div
      data-slot="skeleton"
      className={cn("animate-pulse rounded-md bg-muted", className)} // Assuming bg-muted is defined in Tailwind config
      {...props}
    />
  )
}

export { Skeleton }
