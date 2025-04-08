"use client"
import { useSidebar } from "../components/ui/sidebar"
import { Button } from "../components/ui/button"
import { Menu } from "lucide-react"

export function MobileSidebarTrigger() {
    const { setOpenMobile } = useSidebar()

    return (
        <div className="fixed top-4 left-4 z-40 md:hidden">
            <Button
                variant="outline"
                size="icon"
                className="h-10 w-10 rounded-full bg-background shadow-md"
                onClick={() => setOpenMobile(true)}
            >
                <Menu className="h-5 w-5" />
                <span className="sr-only">Open menu</span>
            </Button>
        </div>
    )
}

