import { BrowserRouter as Router, Route, Routes } from 'react-router-dom'
import {SidebarProvider, SidebarInset} from "../src/components/ui/sidebar"
import { Toaster } from "../src/components/ui/sonner"
import { AppSidebar } from "../src/components/app-sidebar"
import Dashboard from "./pages/dashboard.tsx"
import Jobs from "./pages/Jobs"
import JobDetail from "./pages/JobDetail"
import Analytics from "./pages/analytics"
import Settings from "./pages/settings"
import { MobileSidebarTrigger } from "./components/mobile-sidebar-trigger"

function App() {
    return (
        <Router>
            <SidebarProvider>
                <div className="flex w-full h-screen">
                    <AppSidebar />
                    <SidebarInset className="flex-1 overflow-auto">
                        <MobileSidebarTrigger />
                        <Routes>
                            <Route path="/" element={<Dashboard />} />
                            <Route path="/jobs" element={<Jobs />} />
                            <Route path="/jobs/:id" element={<JobDetail />} />
                            <Route path="/analytics" element={<Analytics />} />
                            <Route path="/settings" element={<Settings />} />
                        </Routes>
                    </SidebarInset>
                </div>
                <Toaster />
            </SidebarProvider>
        </Router>
    )
}

export default App
