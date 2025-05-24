import './App.css'

import { BrowserRouter as Router, Route, Routes, Navigate } from "react-router-dom"
import { useState, useEffect } from "react"
import { SidebarProvider, SidebarInset } from "./components/ui/sidebar"
import { ThemeProvider } from "./components/theme-provider"
import { Toaster } from "./components/ui/sonner"
import { AppSidebar } from "./components/app-sidebar"
import Home from "./pages/home.tsx"
import Dashboard from "./pages/dashboard"
import Jobs from "./pages/Jobs"
import JobDetail from "./pages/JobDetail"
import Analytics from "./pages/analytics"
import Settings from "./pages/settings"
import JobVerification from "./pages/JobVerification"
import { SourceLoginPage as Login } from "./pages/Auth/login"
import Register from "./pages/Auth/register"
import { MobileSidebarTrigger } from "./components/mobile-sidebar-trigger"
import { setupAxiosInterceptors, isAuthenticated as checkAuthentication, getCurrentUser } from './services/authService'

// Match the User interface from login.tsx
interface User {
  username: string
  password: string
  role: string
}

function App() {
  // Initialize authentication state immediately from localStorage to prevent redirect issues
  const [isAuthenticated, setIsAuthenticated] = useState(() => checkAuthentication())
  const [userRole, setUserRole] = useState<string | null>(() => {
    const user = getCurrentUser();
    return user && user.roles.length > 0 ? user.roles[0] : null;
  })
  const [isLoading, setIsLoading] = useState(true)

  useEffect(() => {
    // Setup axios interceptors for authentication
    setupAxiosInterceptors();

    // Double-check authentication state
    const authStatus = checkAuthentication();
    setIsAuthenticated(authStatus);
    
    if (authStatus) {
      const user = getCurrentUser();
      if (user && user.roles.length > 0) {
        setUserRole(user.roles[0]);
      }
    }
    
    // Set loading to false after authentication check
    setIsLoading(false);
  }, [])

  const handleLogin = (user: User) => {
    // Token is now handled by the login function in authService
    // We just need to update the state
    setIsAuthenticated(true)
    setUserRole(user.role)
  }

  const handleLogout = () => {
    // Use the logout function from authService
    import('./services/authService').then(({ logout }) => {
      logout();
      setIsAuthenticated(false);
      setUserRole(null);
    });
  }

  // Show loading spinner while checking authentication
  if (isLoading) {
    return (
      <div className="flex items-center justify-center min-h-screen">
        <div className="animate-spin rounded-full h-32 w-32 border-b-2 border-gray-900"></div>
      </div>
    );
  }

  return (
      <Router>
        <ThemeProvider defaultTheme="system" storageKey="ui-theme">
          <Routes>
            {/* Public routes that don't require authentication */}
            <Route path="/" element={<Home onLogin={() => (window.location.href = "/login")} />} />
            <Route path="/login" element={<Login onLogin={handleLogin} />} />
            <Route path="/register" element={<Register />} />

            {/* Protected routes that require authentication */}
            {isAuthenticated ? (
                <Route
                    path="/*"
                    element={
                      <SidebarProvider>
                        <div className="flex w-full h-screen">
                          <AppSidebar userRole={userRole} onLogout={handleLogout} />
                          <SidebarInset className="flex-1 overflow-auto">
                            <MobileSidebarTrigger />
                            <Routes>
                              <Route path="/dashboard" element={<Dashboard />} />
                              <Route path="/jobs" element={<Jobs />} />
                              <Route path="/jobs/:id" element={<JobDetail />} />
                              <Route path="/analytics" element={<Analytics />} />
                              <Route path="/job-verification" element={<JobVerification />} />
                              <Route path="/settings" element={<Settings />} />
                              <Route path="*" element={<Navigate to="/dashboard" replace />} />
                            </Routes>
                          </SidebarInset>
                        </div>
                        <Toaster />
                      </SidebarProvider>
                    }
                />
            ) : (
                // Redirect to login for protected routes when not authenticated
                <Route path="/*" element={<Navigate to="/login" replace />} />
            )}
          </Routes>
        </ThemeProvider>
      </Router>
  )
}

export default App
