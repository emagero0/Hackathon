import * as React from "react"
import * as TooltipPrimitive from "@radix-ui/react-tooltip"

import { cn } from "../../lib/utils" // Ensure this path is correct

// Note: This component relies on specific Tailwind animations/classes being defined:
// animate-in, fade-in-0, zoom-in-95, animate-out, fade-out-0, zoom-out-95, slide-in-from-*
// bg-primary, text-primary-foreground, fill-primary

function TooltipProvider({
  delayDuration = 700, // Default delay from shadcn/ui
  skipDelayDuration = 300, // Default skip delay from shadcn/ui
  ...props
}: React.ComponentProps<typeof TooltipPrimitive.Provider>) {
  return (
    <TooltipPrimitive.Provider
      delayDuration={delayDuration}
      skipDelayDuration={skipDelayDuration}
      {...props} // Pass rest of the props
    />
  )
}

function Tooltip({
  ...props
}: React.ComponentProps<typeof TooltipPrimitive.Root>) {
  // TooltipPrimitive.Root doesn't need TooltipProvider directly wrapping it here
  // TooltipProvider should wrap the application or relevant part once
  return <TooltipPrimitive.Root data-slot="tooltip" {...props} />
}

function TooltipTrigger({
  ...props
}: React.ComponentProps<typeof TooltipPrimitive.Trigger>) {
  return <TooltipPrimitive.Trigger data-slot="tooltip-trigger" {...props} />
}

function TooltipContent({
  className,
  sideOffset = 4, // Default side offset from shadcn/ui
  children,
  ...props
}: React.ComponentProps<typeof TooltipPrimitive.Content>) {
  return (
    <TooltipPrimitive.Portal>
      <TooltipPrimitive.Content
        data-slot="tooltip-content"
        sideOffset={sideOffset}
        className={cn(
          "z-50 overflow-hidden rounded-md border bg-popover px-3 py-1.5 text-sm text-popover-foreground shadow-md animate-in fade-in-0 zoom-in-95 data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=closed]:zoom-out-95 data-[side=bottom]:slide-in-from-top-2 data-[side=left]:slide-in-from-right-2 data-[side=right]:slide-in-from-left-2 data-[side=top]:slide-in-from-bottom-2",
          // Adjusted classes to match typical shadcn/ui setup (border, bg-popover, text-popover-foreground)
          // Removed the arrow part as it's often omitted or styled differently
          className
        )}
        {...props}
      >
        {children}
        {/* Arrow removed for simplicity, can be added back if needed */}
        {/* <TooltipPrimitive.Arrow className="fill-popover" /> */}
      </TooltipPrimitive.Content>
    </TooltipPrimitive.Portal>
  )
}

export { Tooltip, TooltipTrigger, TooltipContent, TooltipProvider }
