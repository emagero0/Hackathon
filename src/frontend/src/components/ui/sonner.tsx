import { Toaster as Sonner, ToasterProps } from "sonner"
import React from "react"; // Import React

// Simplified version without next-themes dependency
const Toaster = ({ ...props }: ToasterProps) => {
  // Hardcode theme or use a simpler detection if needed, e.g., prefers-color-scheme
  // For now, let's default to 'light' or remove the theme prop if sonner handles system default
  const theme = window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';

  return (
    <Sonner
      // theme={theme as ToasterProps["theme"]} // Temporarily removed or set explicitly
      theme={theme} // Use the detected theme
      className="toaster group"
      // Assuming these CSS variables are defined in the global CSS or Tailwind config
      style={
        {
          "--normal-bg": "var(--popover)",
          "--normal-text": "var(--popover-foreground)",
          "--normal-border": "var(--border)",
        } as React.CSSProperties
      }
      {...props}
    />
  )
}

export { Toaster }
